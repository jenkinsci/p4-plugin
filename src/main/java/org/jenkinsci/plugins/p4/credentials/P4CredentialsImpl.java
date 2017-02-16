package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;

public class P4CredentialsImpl {
	
	static public ListBoxModel doFillCredentialItems(Item project, String credentialsId) {

		if(project == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
				project != null && !project.hasPermission(Item.EXTENDED_READ)) {
			return new StandardListBoxModel().includeCurrentValue(credentialsId);
		}

		CredentialsMatchers.instanceOf(P4BaseCredentials.class);

		return new StandardListBoxModel()
				.includeEmptyValue()
				.includeMatchingAs(
						project instanceof Queue.Task
								? Tasks.getAuthenticationOf((Queue.Task) project)
								: ACL.SYSTEM,
						project,
						P4BaseCredentials.class,
						Collections.<DomainRequirement>emptyList(),
						CredentialsMatchers.instanceOf(P4BaseCredentials.class))
				.includeCurrentValue(credentialsId);
	}

	@Deprecated
	static public FormValidation doCheckCredential(@QueryParameter String value) {
		if (value == null) {
			return FormValidation.ok();
		}
		try {
			ConnectionHelper p4 = new ConnectionHelper(value, null);
			if (!p4.login()) {
				return FormValidation
						.error("Authentication Error: Unable to login.");
			}
			if (!p4.checkVersion(20121)) {
				return FormValidation
						.error("Server version is too old (min 2012.1)");
			}
			return FormValidation.ok();
		} catch (Exception e) {
			return FormValidation.error(e.getMessage());
		}
	}

	static public FormValidation doCheckCredential(Item project, String value) {
		if (value == null) {
			return FormValidation.ok();
		}
		try {
			ConnectionHelper p4 = new ConnectionHelper(project, value, null);
			if (!p4.login()) {
				return FormValidation
						.error("Authentication Error: Unable to login.");
			}
			if (!p4.checkVersion(20121)) {
				return FormValidation
						.error("Server version is too old (min 2012.1)");
			}
			return FormValidation.ok();
		} catch (Exception e) {
			return FormValidation.error(e.getMessage());
		}
	}
}
