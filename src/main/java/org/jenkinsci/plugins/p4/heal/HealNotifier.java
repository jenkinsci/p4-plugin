package org.jenkinsci.plugins.p4.heal;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import jenkins.scm.RunWithSCM;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.heal.agent.HealModels;
import org.jenkinsci.plugins.p4.heal.context.FailureContext;
import org.jenkinsci.plugins.p4.heal.context.GuidelineLoader;
import org.jenkinsci.plugins.p4.heal.patch.PatchGuard;
import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;
import org.jenkinsci.plugins.p4.heal.tools.GlobTool;
import org.jenkinsci.plugins.p4.heal.tools.GrepTool;
import org.jenkinsci.plugins.p4.heal.tools.ReadFileTool;
import org.jenkinsci.plugins.p4.heal.verify.BuildVerifier;
import org.jenkinsci.plugins.p4.heal.verify.JobCommands;
import org.jenkinsci.plugins.p4.heal.verify.TestResults;
import org.jenkinsci.plugins.p4.heal.verify.VerifyConfig;
import org.jenkinsci.plugins.p4.heal.verify.VerifyLadder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Attempts an AI-generated fix when a build fails, and only hands one over if it
 * has been proved to work.
 *
 * <p>Off unless an administrator enables it globally and someone adds this step
 * to a job, and in dry-run mode by default so a first release cannot touch the
 * depot.
 */
public class HealNotifier extends Notifier {

	private static final int LOG_TAIL_LINES = 2000;
	private static final int GUIDELINE_BYTE_CAP = 200_000;

	private final AiProvider aiProvider;

	// Blank by default: the commands are taken from the job's own build step unless
	// someone deliberately overrides them.
	private String compileCommand = "";
	private String verifyCommand = "";
	private String targetedTestCommand = "";
	private String allowedPaths = "src/";
	private boolean dryRun = true;
	private boolean verifyShelf = true;
	private boolean allowBuildConfigChanges;
	private int maxAttempts = 3;
	private int criticQuorum = 2;
	private int flakeReruns = 2;
	private long maxTokens;
	private String smartModel = "claude-opus-5";
	private String cheapModel = "claude-haiku-4-5";
	private String smartEffort = "xhigh";
	// Blank: the default critic model does not accept an effort.
	private String cheapEffort = "";
	private int maxResponseTokens = 16000;

	@DataBoundConstructor
	public HealNotifier(AiProvider aiProvider) {
		this.aiProvider = aiProvider;
	}

	public AiProvider getAiProvider() {
		return aiProvider;
	}

	public String getCompileCommand() {
		return compileCommand;
	}

	@DataBoundSetter
	public void setCompileCommand(String compileCommand) {
		this.compileCommand = Util.fixNull(compileCommand);
	}

	public String getVerifyCommand() {
		return verifyCommand;
	}

	@DataBoundSetter
	public void setVerifyCommand(String verifyCommand) {
		this.verifyCommand = Util.fixNull(verifyCommand);
	}

	public String getTargetedTestCommand() {
		return targetedTestCommand;
	}

	@DataBoundSetter
	public void setTargetedTestCommand(String targetedTestCommand) {
		this.targetedTestCommand = Util.fixNull(targetedTestCommand);
	}

	public String getAllowedPaths() {
		return allowedPaths;
	}

	@DataBoundSetter
	public void setAllowedPaths(String allowedPaths) {
		this.allowedPaths = Util.fixNull(allowedPaths);
	}

	public boolean isDryRun() {
		return dryRun;
	}

	@DataBoundSetter
	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

	public boolean isVerifyShelf() {
		return verifyShelf;
	}

	@DataBoundSetter
	public void setVerifyShelf(boolean verifyShelf) {
		this.verifyShelf = verifyShelf;
	}

	public boolean isAllowBuildConfigChanges() {
		return allowBuildConfigChanges;
	}

	@DataBoundSetter
	public void setAllowBuildConfigChanges(boolean allowBuildConfigChanges) {
		this.allowBuildConfigChanges = allowBuildConfigChanges;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	@DataBoundSetter
	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public int getCriticQuorum() {
		return criticQuorum;
	}

	@DataBoundSetter
	public void setCriticQuorum(int criticQuorum) {
		this.criticQuorum = criticQuorum;
	}

	public int getFlakeReruns() {
		return flakeReruns;
	}

	@DataBoundSetter
	public void setFlakeReruns(int flakeReruns) {
		this.flakeReruns = flakeReruns;
	}

	public long getMaxTokens() {
		return maxTokens;
	}

	@DataBoundSetter
	public void setMaxTokens(long maxTokens) {
		this.maxTokens = maxTokens;
	}

	public String getSmartModel() {
		return smartModel;
	}

	@DataBoundSetter
	public void setSmartModel(String smartModel) {
		this.smartModel = smartModel;
	}

	public String getCheapModel() {
		return cheapModel;
	}

	@DataBoundSetter
	public void setCheapModel(String cheapModel) {
		this.cheapModel = cheapModel;
	}

	public String getSmartEffort() {
		return smartEffort;
	}

	@DataBoundSetter
	public void setSmartEffort(String smartEffort) {
		this.smartEffort = smartEffort;
	}

	public String getCheapEffort() {
		return cheapEffort;
	}

	@DataBoundSetter
	public void setCheapEffort(String cheapEffort) {
		this.cheapEffort = cheapEffort;
	}

	public int getMaxResponseTokens() {
		return maxResponseTokens;
	}

	@DataBoundSetter
	public void setMaxResponseTokens(int maxResponseTokens) {
		this.maxResponseTokens = maxResponseTokens;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {

		// A post-build action runs on every build, so this path has to decide for
		// itself whether the build actually failed. The Pipeline step does not:
		// calling it is already the decision.
		if (!shouldHeal(build.getResult())) {
			return true;
		}

		FilePath workspace = build.getWorkspace();
		if (workspace == null) {
			listener.getLogger().println("[p4-heal] No workspace available; skipping.");
			return true;
		}
		attemptHeal(build, workspace, launcher, listener);
		return true;
	}

	/**
	 * Decide whether a verdict is one healing can do anything about.
	 *
	 * <p>Only a build that failed on its own qualifies. {@code ABORTED} and
	 * {@code NOT_BUILT} sort worse than {@code FAILURE}, but they mean the build
	 * never reached the point of failing, so there is nothing to diagnose.
	 *
	 * @param result the build's verdict, or null while it is still running
	 * @return true if healing should be attempted
	 */
	static boolean shouldHeal(Result result) {
		return Result.FAILURE.equals(result);
	}

	/**
	 * The shared entry point for both job types.
	 *
	 * <p>Freestyle reaches it through {@link #perform}; Pipeline reaches it through
	 * {@code HealNotifierStep}. Everything below this line works from {@link Run},
	 * so the two paths cannot drift apart.
	 *
	 * <p>Does not itself check whether the build failed. In a scripted pipeline the
	 * result is often still unset inside a {@code catch}, and refusing to run there
	 * would make the step silently do nothing in one of its most obvious uses.
	 * Callers that run unconditionally must check the result themselves.
	 *
	 * @param run       the build to heal
	 * @param workspace its workspace
	 * @param launcher  launcher for the node the workspace is on
	 * @param listener  build listener
	 * @throws InterruptedException if the build was cancelled
	 */
	public void attemptHeal(Run<?, ?> run, FilePath workspace, Launcher launcher,
	                        TaskListener listener) throws InterruptedException {

		if (!isGloballyEnabled()) {
			listener.getLogger().println("[p4-heal] AI healing is disabled globally; skipping.");
			return;
		}

		// The whole feature is best-effort. A build that already failed must not
		// fail differently because healing went wrong.
		try {
			HealOutcome outcome = heal(run, workspace, launcher, listener);
			listener.getLogger().println("[p4-heal] " + outcome.status() + ": " + outcome.detail());
			listener.getLogger().println("[p4-heal] tokens in=" + outcome.inputTokens()
					+ " out=" + outcome.outputTokens());
			if (outcome.healed()) {
				listener.getLogger().println("[p4-heal] delivered: " + outcome.delivered());
			}
		} catch (IOException | RuntimeException e) {
			// Not just IOException: a misconfigured provider or a bug in here throws
			// unchecked, and letting that out would mark the step, and so the build,
			// as failed by the feature meant to help it.
			listener.getLogger().println("[p4-heal] healing could not run: " + e.getMessage());
		}
	}

	private HealOutcome heal(Run<?, ?> run, FilePath workspace, Launcher launcher,
	                         TaskListener listener) throws IOException, InterruptedException {

		EnvVars environment = run.getEnvironment(listener);

		// Worked out before anything expensive: without a command that proves a fix,
		// every later gate would pass on nothing.
		VerifyConfig verifyConfig = verifyConfig(run, environment, workspace, launcher, listener);
		if (verifyConfig.verifyCommand().isBlank()) {
			return new HealOutcome(HealOutcome.Status.ERROR, "",
					"Nothing to verify a fix with: this job has no Maven build step to take a "
							+ "command from. Set a verify command on the heal step.", 0, 0);
		}

		BuildVerifier verifier = new BuildVerifier(workspace, launcher, listener, environment);

		TestResults baseline = verifier.readTestResults();
		FailureContext context = new FailureContext(
				FailureContext.excerpt(run.getLog(LOG_TAIL_LINES)),
				baseline.getFailures(),
				changedFiles(run));

		String guidelines = new GuidelineLoader(GuidelineLoader.DEFAULT_GLOBS, GUIDELINE_BYTE_CAP)
				.load(workspace);
		listener.getLogger().println("[p4-heal] repository guidelines loaded: "
				+ guidelines.length() + " characters");

		List<AiTool> tools = List.of(
				new ReadFileTool(workspace), new GrepTool(workspace), new GlobTool(workspace));

		VerifyLadder ladder = new VerifyLadder(verifier, verifyConfig);

		ShelveDelivery delivery = new ShelveDelivery(run, workspace, listener);
		if (verifyShelf) {
			delivery.setVerifier(
					new ShelfVerifier(run, launcher, listener, environment, verifyConfig));
		}

		HealOrchestrator orchestrator = new HealOrchestrator(
				aiProvider,
				new HealModels(smartModel, cheapModel, smartEffort, cheapEffort, maxResponseTokens),
				tools,
				new PatchGuard(splitPaths(allowedPaths), allowBuildConfigChanges),
				ladder,
				new WorkspacePatchSession(workspace, listener, dryRun, delivery),
				new HealLimits(maxAttempts, criticQuorum, maxTokens),
				listener);

		return orchestrator.run(guidelines, context, baseline);
	}

	/**
	 * Work out what to build with.
	 *
	 * <p>A command set on this step always wins; anything left blank is taken from
	 * the job's own build step. That way the fix is proved against the build that
	 * actually failed, rather than against a second copy of it that has to be kept
	 * in step by hand.
	 *
	 * <p>Resolved once and shared, so the gates in the build's workspace and the
	 * rebuild of the shelved change cannot disagree about what "builds" means.
	 */
	private VerifyConfig verifyConfig(Run<?, ?> run, EnvVars environment, FilePath workspace,
	                                  Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		JobCommands job =
				JobCommands.from(run, environment, workspace, launcher, listener).orElse(null);

		VerifyConfig config = new VerifyConfig(
				inherited(compileCommand, job == null ? "" : job.compile()),
				inherited(targetedTestCommand, job == null ? "" : job.targeted()),
				inherited(verifyCommand, job == null ? "" : job.verify()),
				flakeReruns);

		listener.getLogger().println("[p4-heal] compile: " + config.compileCommand());
		listener.getLogger().println("[p4-heal] verify: " + config.verifyCommand());
		listener.getLogger().println("[p4-heal] targeted tests: " + config.targetedTestCommand());
		return config;
	}

	private static String inherited(String configured, String fromJob) {
		return StringUtils.defaultIfBlank(configured, fromJob);
	}

	/**
	 * Changed files come from {@link RunWithSCM}, which both {@code AbstractBuild}
	 * and {@code WorkflowRun} implement — {@code getChangeSets()} is not on
	 * {@link Run} itself.
	 */
	private static List<String> changedFiles(Run<?, ?> run) {
		List<String> files = new ArrayList<>();
		if (!(run instanceof RunWithSCM<?, ?> withScm)) {
			return files;
		}
		for (ChangeLogSet<? extends ChangeLogSet.Entry> set : withScm.getChangeSets()) {
			for (ChangeLogSet.Entry entry : set) {
				files.addAll(entry.getAffectedPaths());
			}
		}
		return files;
	}

	private static List<String> splitPaths(String configured) {
		return List.of(Util.tokenize(configured, ",\n\r\t "));
	}

	private static boolean isGloballyEnabled() {
		PerforceScm.DescriptorImpl descriptor =
				Jenkins.get().getDescriptorByType(PerforceScm.DescriptorImpl.class);
		return descriptor != null && descriptor.isAiHealEnabled();
	}

	@Extension
	@Symbol("heal")
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4: Attempt an AI fix when the build fails";
		}

		/**
		 * Hidden entirely until an administrator turns the feature on, so a job
		 * cannot be configured to call a paid API by accident.
		 */
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return isGloballyEnabled();
		}
	}
}
