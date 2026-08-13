package org.jenkinsci.plugins.p4.workflow;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.heal.HealNotifierStep;
import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.Set;

/**
 * Pipeline step: attempt an AI fix for a failed build.
 *
 * <p>Unlike the Freestyle post-build action, a Pipeline step runs where it is
 * written rather than after the build finishes, so the pipeline must decide when
 * to call it — normally in a {@code post { failure { ... } }} block, or in a
 * {@code catch} around the build stage.
 *
 * <pre>
 * post {
 *     failure {
 *         p4heal(
 *             aiProvider: claude(credentialId: 'anthropic-api-key'),
 *             compileCommand: 'mvn -B compile',
 *             verifyCommand: 'mvn -B verify')
 *     }
 * }
 * </pre>
 */
public class P4HealStep extends Step {

	private final AiProvider aiProvider;
	private final String compileCommand;
	private final String verifyCommand;

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
	public P4HealStep(AiProvider aiProvider, String compileCommand, String verifyCommand) {
		this.aiProvider = aiProvider;
		this.compileCommand = compileCommand;
		this.verifyCommand = verifyCommand;
	}

	public AiProvider getAiProvider() {
		return aiProvider;
	}

	public String getCompileCommand() {
		return compileCommand;
	}

	public String getVerifyCommand() {
		return verifyCommand;
	}

	public String getTargetedTestCommand() {
		return targetedTestCommand;
	}

	@DataBoundSetter
	public void setTargetedTestCommand(String targetedTestCommand) {
		this.targetedTestCommand = targetedTestCommand;
	}

	public String getAllowedPaths() {
		return allowedPaths;
	}

	@DataBoundSetter
	public void setAllowedPaths(String allowedPaths) {
		this.allowedPaths = allowedPaths;
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
	public StepExecution start(StepContext context) {
		return new P4HealStepExecution(this, context);
	}

	@Extension(optional = true)
	@Symbol("heal")
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4heal";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Heal: attempt an AI fix for a failed build";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
		}
	}

	public static class P4HealStepExecution extends SynchronousNonBlockingStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		private transient P4HealStep step;

		protected P4HealStepExecution(P4HealStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			// The global switch is not checked here: HealNotifier.attemptHeal already
			// does it and prints the same message on the same listener, and one home
			// for that policy is what keeps the two entry points behaving alike.
			HealNotifierStep notifier = new HealNotifierStep(
					step.getAiProvider(), step.getCompileCommand(), step.getVerifyCommand());
			notifier.setTargetedTestCommand(step.getTargetedTestCommand());
			notifier.setAllowedPaths(step.getAllowedPaths());
			notifier.setDryRun(step.isDryRun());
			notifier.setVerifyShelf(step.isVerifyShelf());
			notifier.setAllowBuildConfigChanges(step.isAllowBuildConfigChanges());
			notifier.setMaxAttempts(step.getMaxAttempts());
			notifier.setCriticQuorum(step.getCriticQuorum());
			notifier.setFlakeReruns(step.getFlakeReruns());
			notifier.setMaxTokens(step.getMaxTokens());
			notifier.setSmartModel(step.getSmartModel());
			notifier.setCheapModel(step.getCheapModel());
			notifier.setSmartEffort(step.getSmartEffort());
			notifier.setCheapEffort(step.getCheapEffort());
			notifier.setMaxResponseTokens(step.getMaxResponseTokens());

			notifier.perform(
					getContext().get(Run.class),
					getContext().get(FilePath.class),
					getContext().get(Launcher.class),
					getContext().get(TaskListener.class));
			return null;
		}
	}
}
