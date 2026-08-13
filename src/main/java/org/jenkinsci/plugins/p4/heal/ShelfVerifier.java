package org.jenkinsci.plugins.p4.heal;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.heal.verify.BuildVerifier;
import org.jenkinsci.plugins.p4.heal.verify.VerifyConfig;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.tasks.CheckoutTask;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.tasks.UnshelveTask;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;

/**
 * Builds a shelved change on its own, in a workspace nothing else has touched.
 *
 * <p>The gates that ran before the shelf ran in the build's own workspace, which
 * still holds compiler output, test reports and whatever else the build left
 * behind. This proves the change stands up without any of that: a fresh client
 * synced to the same revision the build used, the shelf unshelved into it, and
 * the configured commands run there.
 *
 * <p>The client and its files are removed afterwards, pass or fail.
 */
public class ShelfVerifier {

	private static final String CLIENT_SUFFIX = "-heal";

	private final Run<?, ?> run;
	private final Launcher launcher;
	private final TaskListener listener;
	private final EnvVars environment;
	private final VerifyConfig config;
	private final Build build;

	/**
	 * @param run         the build being healed
	 * @param launcher    launcher for the node the workspace is on
	 * @param listener    build listener, for the console
	 * @param environment the build's resolved environment, passed in rather than
	 *                    resolved here: {@code Run.getEnvironment} walks every
	 *                    contributor and reaches the agent for its environment, and
	 *                    the caller has already paid for it once
	 * @param config      the commands to build the shelved change with
	 */
	public ShelfVerifier(Run<?, ?> run, Launcher launcher, TaskListener listener,
	                     EnvVars environment, VerifyConfig config) {
		this(run, launcher, listener, environment, config, null);
	}

	ShelfVerifier(Run<?, ?> run, Launcher launcher, TaskListener listener, EnvVars environment,
	              VerifyConfig config, Build build) {
		this.run = run;
		this.launcher = launcher;
		this.listener = listener;
		this.environment = environment;
		this.config = config;
		this.build = build == null ? this::runCommand : build;
	}

	/**
	 * How a command is run against the unshelved change. Separated so the Perforce
	 * half can be exercised without running a real build.
	 */
	interface Build {

		/**
		 * @param workspace the clean workspace holding the unshelved change
		 * @param command   the command to run there
		 * @return true if it succeeded
		 * @throws IOException          if the command could not be launched
		 * @throws InterruptedException if the build was cancelled
		 */
		boolean succeeds(FilePath workspace, String command) throws IOException, InterruptedException;
	}

	/**
	 * Sync, unshelve and build, then take the workspace away again.
	 *
	 * @param credential     Perforce credential the build used
	 * @param templateClient the build's client, used as the template for a fresh one
	 * @param revision       the revision the build synced to
	 * @param temp           an empty directory to use as the client root
	 * @param shelf          the shelved changelist to verify
	 * @return true if the shelved change built on its own
	 * @throws IOException          if Perforce or a command could not be reached
	 * @throws InterruptedException if the build was cancelled
	 */
	public boolean verify(String credential, String templateClient, P4Ref revision, FilePath temp,
	                      long shelf) throws IOException, InterruptedException {

		listener.getLogger().println("[p4-heal] building shelved change " + shelf
				+ " in a clean workspace");

		Workspace spec = new TemplateWorkspaceImpl("none", false, templateClient,
				templateClient + CLIENT_SUFFIX);
		temp.mkdirs();

		try {
			sync(credential, spec, temp, revision);
			unshelve(credential, spec, temp, shelf);
			return ran(temp, config.compileCommand()) && ran(temp, config.verifyCommand());
		} finally {
			cleanup(credential, spec, temp);
		}
	}

	/**
	 * Run one command and say how it went.
	 *
	 * <p>Only the verdict is printed. The output itself is what the model reads,
	 * and repeating a full build log on the console would bury the one line a human
	 * is looking for.
	 */
	private boolean ran(FilePath temp, String command) throws IOException, InterruptedException {
		boolean ok = build.succeeds(temp, command);
		listener.getLogger().println("[p4-heal] clean workspace: " + command
				+ (ok ? " — passed" : " — failed"));
		return ok;
	}

	private void sync(String credential, Workspace spec, FilePath temp, P4Ref revision)
			throws IOException, InterruptedException {

		// Force clean: the point of this workspace is that nothing else has been
		// near it, so the sync must not trust a have list it did not write.
		CheckoutTask task = new CheckoutTask(credential, run, listener,
				new ForceCleanImpl(false, false, null, null));
		task.setBuildChange(revision);
		task.setWorkspace(task.setEnvironment(run, spec, temp));
		temp.act(task);
	}

	private void unshelve(String credential, Workspace spec, FilePath temp, long shelf)
			throws IOException, InterruptedException {

		UnshelveTask task = new UnshelveTask(credential, run, listener, "none", false);
		task.setShelf(shelf);
		task.setWorkspace(task.setEnvironment(run, spec, temp));
		temp.act(task);
	}

	private boolean runCommand(FilePath temp, String command)
			throws IOException, InterruptedException {

		return new BuildVerifier(temp, launcher, listener, environment).run(command).ok();
	}

	/**
	 * Take the throwaway client and its files away.
	 *
	 * <p>Failing to clean up says nothing about the change, so it is logged rather
	 * than thrown: letting it out would replace a real verdict with a tidy-up
	 * error.
	 */
	private void cleanup(String credential, Workspace spec, FilePath temp) {
		try {
			RemoveClientTask task = new RemoveClientTask(credential, run, listener);
			task.setDeleteClient(true);
			task.setDeleteFiles(true);
			task.setWorkspace(task.setEnvironment(run, spec, temp));
			temp.act(task);
			temp.deleteRecursive();
		} catch (IOException | InterruptedException | RuntimeException e) {
			listener.getLogger().println("[p4-heal] could not remove the verification workspace: "
					+ e.getMessage());
		}
	}
}
