package org.jenkinsci.plugins.p4.workflow;

import javax.inject.Inject;

import org.jenkinsci.plugins.p4.cleanup.CleanupNotifier;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

public class P4CleanupStep extends AbstractStepImpl {

	public final boolean deleteClient;

	public boolean isDeleteClient() {
		return deleteClient;
	}
	
	@DataBoundConstructor
	public P4CleanupStep(boolean deleteClient) {
		this.deleteClient = deleteClient;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
		public DescriptorImpl() {
			super(P4CleanupStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "p4cleanup";
		}

		@Override
		public String getDisplayName() {
			return "P4 Cleanup";
		}
	}

	public static class P4CleanupStepExecution extends AbstractSynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		@Inject private transient P4CleanupStep step;
		@StepContextParameter private transient Run<?, ?> run;
		@StepContextParameter private transient FilePath workspace;
		@StepContextParameter private transient TaskListener listener;
		@StepContextParameter private transient Launcher launcher;

		@Override
		protected Void run() throws Exception {
			CleanupNotifier notifier = new CleanupNotifier(step.isDeleteClient());
			notifier.perform(run, workspace, launcher, listener);
			return null;
		}

	}
}
