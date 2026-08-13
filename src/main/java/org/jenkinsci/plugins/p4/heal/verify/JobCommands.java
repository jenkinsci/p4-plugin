package org.jenkinsci.plugins.p4.heal.verify;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.util.ArgumentListBuilder;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The commands a job already runs, read back out of its own build steps.
 *
 * <p>A job that builds something has said how to build it once. Asking for the
 * same commands again on the healing step invites the two to drift, and a fix
 * verified with a command the job never runs has not really been verified.
 *
 * <p>Only {@link Maven} build steps are read. A shell step's script is a whole
 * program — multi-line, with operators — and {@link BuildVerifier} launches a
 * tokenised command line with no shell to interpret it, so there is no honest way
 * to turn one into a command string. Those jobs have to say what to run.
 *
 * <p>The result is plain strings, which is what lets the same commands run both in
 * the build's own workspace and in the clean workspace a shelf is verified in.
 */
public final class JobCommands {

	private static final Logger logger = Logger.getLogger(JobCommands.class.getName());

	/**
	 * Used when the job names no Maven installation, matching what the job itself
	 * falls back to.
	 */
	private static final String DEFAULT_EXECUTABLE = "mvn";

	private final String prefix;
	private final String goals;

	JobCommands(String prefix, String goals) {
		this.prefix = prefix;
		this.goals = goals;
	}

	/**
	 * Read the commands out of the build's job.
	 *
	 * @param run         the build being healed
	 * @param environment the build's environment, used to expand the build step
	 * @param workspace   the build's workspace, which names the node the Maven
	 *                    installation has to be resolved for
	 * @param launcher    launcher for the node the build ran on
	 * @param listener    build listener, for resolving the Maven installation
	 * @return the job's commands, or empty if it has no Maven build step
	 * @throws IOException          if the installation could not be reached
	 * @throws InterruptedException if the build was cancelled
	 */
	public static Optional<JobCommands> from(Run<?, ?> run, EnvVars environment, FilePath workspace,
	                                         Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		Optional<Maven> maven = mavenStep(run.getParent());
		if (maven.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(of(maven.get(), environment,
				executable(maven.get(), environment, workspace, launcher, listener)));
	}

	/**
	 * Find the build step to copy.
	 *
	 * <p>Only a {@link Project} has a build step list; a Pipeline job does not, so
	 * there is nothing to inherit there and the step's own commands stand.
	 *
	 * @param job the job to read
	 * @return its first Maven build step, or empty
	 */
	static Optional<Maven> mavenStep(Job<?, ?> job) {
		if (!(job instanceof Project<?, ?> project)) {
			return Optional.empty();
		}
		for (Builder builder : project.getBuilders()) {
			if (builder instanceof Maven maven) {
				return Optional.of(maven);
			}
		}
		return Optional.empty();
	}

	/**
	 * Render the command lines, given an already-resolved executable.
	 *
	 * <p>Separated from {@link #from} so the rendering can be tested without a node,
	 * a launcher or a Maven installation.
	 *
	 * @param maven       the build step to copy
	 * @param environment the build's environment
	 * @param executable  path to the {@code mvn} to run
	 * @return the job's commands
	 * @throws IOException if the build step's properties could not be parsed
	 */
	static JobCommands of(Maven maven, EnvVars environment, String executable) throws IOException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(executable);
		// The output of these commands is read by a model, so nothing interactive
		// and nothing coloured.
		args.add("-B");

		String pom = environment.expand(maven.pom);
		if (pom != null && !pom.isBlank()) {
			args.add("-f", pom);
		}
		args.addKeyValuePairsFromPropertyString("-D", maven.properties,
				new VariableResolver.ByMap<>(environment));

		if (maven.usesPrivateRepository()) {
			// Relative on purpose: it then lands inside whichever workspace the
			// command runs in, which is what the job itself gets.
			args.add("-Dmaven.repo.local=.repository");
		}

		String goals = environment.expand(Util.fixNull(maven.getTargets()))
				.replaceAll("[\t\r\n]+", " ").trim();

		return new JobCommands(args.toStringWithQuote(), goals);
	}

	/**
	 * @return the command that must succeed before anything is tested
	 */
	public String compile() {
		return prefix + " compile";
	}

	/**
	 * @return the command that decides whether a fix is real — the job's own
	 *         goals, so it is the same build that failed
	 */
	public String verify() {
		return prefix + " " + goals;
	}

	/**
	 * @return the command that runs only the originally failing tests, with the
	 *         {@code {tests}} placeholder {@link VerifyConfig} substitutes
	 */
	public String targeted() {
		return prefix + " test -Dtest={tests}";
	}

	/**
	 * Resolve the {@code mvn} the job would use.
	 *
	 * <p>Falls back to whatever is on the {@code PATH} rather than failing: a heal
	 * attempt that cannot name an executable is still worth making, and the command
	 * itself will say so if it is not there.
	 */
	private static String executable(Maven maven, EnvVars environment, FilePath workspace,
	                                 Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		Maven.MavenInstallation installation = maven.getMaven();
		if (installation == null) {
			return DEFAULT_EXECUTABLE;
		}

		// Taken from the workspace rather than Computer.currentComputer(): a Pipeline
		// step runs this on a controller thread, where the ambient computer is not the
		// agent the build is on, and the installation would be resolved for the wrong
		// node — so Freestyle and Pipeline would verify with different toolchains.
		Computer computer = workspace.toComputer();
		Node node = computer == null ? null : computer.getNode();
		if (node != null) {
			installation = installation.forNode(node, listener);
		}

		String executable = installation.forEnvironment(environment).getExecutable(launcher);
		if (executable == null) {
			logger.log(Level.WARNING, "Maven installation {0} has no executable; falling back to {1}",
					new Object[]{installation.getName(), DEFAULT_EXECUTABLE});
			return DEFAULT_EXECUTABLE;
		}
		return executable;
	}
}
