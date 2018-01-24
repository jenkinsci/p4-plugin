package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;

public class ApproveNotifierStep extends ApproveNotifier implements SimpleBuildStep {

	@DataBoundConstructor
	public ApproveNotifierStep(String credential, String review, String status) {
		super(credential, review, status);
	}

	@DataBoundSetter
	public void setDescription(String description) {
		super.setDescription(description);
	}

	@Override
	public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath buildWorkspace,
	                    @Nonnull Launcher launcher, @Nonnull TaskListener listener)
			throws InterruptedException, IOException {

		EnvVars env = run.getEnvironment(listener);

		for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView()) {
			ec.buildEnvironmentFor(run, env, listener);
		}

		ConnectionHelper p4 = new ConnectionHelper(run, getCredential(), listener);

		try {
			approveReview(p4, env);
		} catch (Exception e) {
			throw new IOException("Unable to update Review.", e);
		}
	}
}
