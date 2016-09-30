package org.jenkinsci.plugins.p4.groovy;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;

public class GetP4Step extends AbstractStepImpl {

	private final String credential;
	private final Workspace workspace;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	@DataBoundConstructor
	public GetP4Step(String credential, Workspace workspace) {
		this.credential = credential;
		this.workspace = workspace;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(P4RunCommandStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "p4";
		}

		@Override
		public String getDisplayName() {
			return "P4 Groovy (BETA)";
		}

		public ListBoxModel doFillCredentialItems() {
			return P4CredentialsImpl.doFillCredentialItems();
		}
	}

	public static class P4RunCommandStepExecution extends AbstractSynchronousStepExecution<P4Groovy> {

		private static final long serialVersionUID = 1L;

		@Inject
		private transient GetP4Step step;
		@StepContextParameter
		private transient Run<?, ?> run;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient Launcher launcher;

		@Override
		protected P4Groovy run() throws Exception {
			GetP4 p4Groovy = new GetP4(step.getCredential(), step.getWorkspace());
			p4Groovy.perform(run, workspace, launcher, listener);
			return p4Groovy.getP4Groovy();
		}
	}
}