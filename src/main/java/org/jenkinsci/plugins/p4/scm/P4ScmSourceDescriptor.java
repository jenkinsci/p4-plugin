package org.jenkinsci.plugins.p4.scm;

import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public abstract class P4ScmSourceDescriptor extends SCMSourceDescriptor {

	public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

	@Override
	public String getDisplayName() {
		return "Perforce";
	}

	public ListBoxModel doFillCredentialItems() {
		return P4CredentialsImpl.doFillCredentialItems();
	}

	public FormValidation doCheckCredential(@QueryParameter String value) {
		return P4CredentialsImpl.doCheckCredential(value);
	}

	public ListBoxModel doFillCharsetItems() {
		return WorkspaceDescriptor.doFillCharsetItems();
	}

	public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
		return RepositoryBrowsers.filter(P4Browser.class);
	}

}
