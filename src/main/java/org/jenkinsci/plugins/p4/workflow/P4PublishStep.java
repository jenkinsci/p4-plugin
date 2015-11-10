package org.jenkinsci.plugins.p4.workflow;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import javax.inject.Inject;

import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.publish.PublishNotifierStep;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class P4PublishStep extends AbstractStepImpl {
	private final String credential;
	private final Workspace workspace;
	private final Publish publish;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Publish getPublish() {
		return publish;
	}

	@DataBoundConstructor
	public P4PublishStep(String credential, Workspace workspace, Publish publish) {
		this.credential = credential;
		this.workspace = workspace;
		this.publish = publish;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(P4PublishStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "p4publish";
		}

		@Override
		public String getDisplayName() {
			return "P4 Publish";
		}

		public ListBoxModel doFillCredentialItems() {
			return P4CredentialsImpl.doFillCredentialItems();
		}

		public FormValidation doCheckCredential(@QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(value);
		}

	}

	public static class P4PublishStepExecution extends
			AbstractSynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		@Inject
		private transient P4PublishStep step;
		@StepContextParameter
		private transient Run<?, ?> run;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient Launcher launcher;

		@Override
		protected Void run() throws Exception {
			PublishNotifierStep notifier = new PublishNotifierStep(
					step.getCredential(), step.getWorkspace(),
					step.getPublish());
			notifier.perform(run, workspace, launcher, listener);
			return null;
		}

	}

}
