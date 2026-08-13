package org.jenkinsci.plugins.p4.heal;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The Pipeline-compatible form of {@link HealNotifier}.
 *
 * <p>Adds nothing but a {@link Run}-based entry point — all the behaviour, and
 * every safety check, lives in the superclass, so the Freestyle and Pipeline
 * paths cannot diverge.
 */
public class HealNotifierStep extends HealNotifier implements SimpleBuildStep {

	@DataBoundConstructor
	public HealNotifierStep(AiProvider aiProvider, String compileCommand, String verifyCommand) {
		super(aiProvider);
		setCompileCommand(compileCommand);
		setVerifyCommand(verifyCommand);
	}

	@Override
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace,
	                    @NonNull Launcher launcher, @NonNull TaskListener listener)
			throws InterruptedException {

		attemptHeal(run, workspace, launcher, listener);
	}
}
