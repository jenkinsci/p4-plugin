package org.jenkinsci.plugins.p4.groovy;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.List;

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
			return "P4 Groovy";
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 * 
		 * @return A list of Perforce credential items to populate the jelly
		 *         Select list.
		 */
		public ListBoxModel doFillCredentialItems() {
			ListBoxModel list = new ListBoxModel();

			Class<P4BaseCredentials> type = P4BaseCredentials.class;
			Jenkins scope = Jenkins.getInstance();
			Authentication acl = ACL.SYSTEM;
			DomainRequirement domain = new DomainRequirement();

			List<P4BaseCredentials> credentials;
			credentials = CredentialsProvider.lookupCredentials(type, scope, acl, domain);

			if (credentials.isEmpty()) {
				list.add("Select credential...", null);
			}
			for (P4BaseCredentials c : credentials) {
				StringBuffer sb = new StringBuffer();
				sb.append(c.getDescription());
				sb.append(" (");
				sb.append(c.getUsername());
				sb.append(":");
				sb.append(c.getP4port());
				sb.append(")");
				list.add(sb.toString(), c.getId());
			}
			return list;
		}
	}

	public static class P4RunCommandStepExecution extends AbstractSynchronousStepExecution<P4Groovy> {

		private static final long serialVersionUID = 1L;

		@Inject private transient GetP4Step step;
		@StepContextParameter private transient Run<?, ?> run;
		@StepContextParameter private transient FilePath workspace;
		@StepContextParameter private transient TaskListener listener;
		@StepContextParameter private transient Launcher launcher;

		@Override
		protected P4Groovy run() throws Exception {
			GetP4 p4Groovy = new GetP4(step.getCredential(), step.getWorkspace());
			p4Groovy.perform(run, workspace, launcher, listener);
			return p4Groovy.getP4Groovy();
		}

	}
}