package org.jenkinsci.plugins.p4.scm;

import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public abstract class P4SCMSourceDescriptor extends SCMSourceDescriptor {

	public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}";

	/**
	 * Credentials list, a Jelly config method for a build job.
	 *
	 * @param project    Jenkins project item
	 * @param credential Perforce credential ID
	 * @return A list of Perforce credential items to populate the jelly
	 * Select list.
	 */
	public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
		return P4CredentialsImpl.doFillCredentialItems(project, credential);
	}

	public FormValidation doCheckCredential(@AncestorInPath Item project, @QueryParameter String value) {
		return P4CredentialsImpl.doCheckCredential(project, value);
	}

	public ListBoxModel doFillCharsetItems() {
		return WorkspaceDescriptor.doFillCharsetItems();
	}

	public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
		return RepositoryBrowsers.filter(P4Browser.class);
	}

	public FormValidation doCheckIncludes(@QueryParameter String value) {
		if (value != null && !value.isEmpty()) {
			return FormValidation.ok();
		} else {
			return FormValidation.error("Please provide a valid Includes path.");
		}
	}

	public FormValidation doCheckPath(@QueryParameter String value) {
		if (value == null || value.isEmpty()) {
			return FormValidation.error("Please provide a valid Depot path e.g. //depot/libs");
		}
		if (value.endsWith("...") || value.endsWith("*")) {
			return FormValidation.error("Please remove wild cards from path.");
		}
		if (value.endsWith("/")) {
			return FormValidation.error("Please remove trailing '/' from path.");
		}

		return FormValidation.ok();
	}

// TODO may want to add traits...
/*	// need to implement this as the default filtering of form binding will not be specific enough
	public List<SCMSourceTraitDescriptor> getTraitsDescriptors() {
		return SCMSourceTrait._for(this, MySCMSourceContext.class, MySCMBuilder.class);
	}

	public List<SCMSourceTrait> getTraitsDefaults() {
		return Collections.<SCMSourceTrait>singletonList(new MySCMDiscoverChangeRequests());
	}*/
}
