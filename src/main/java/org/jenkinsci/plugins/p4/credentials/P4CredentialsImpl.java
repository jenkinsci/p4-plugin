package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;
import java.util.List;

public class P4CredentialsImpl {

	@Deprecated
	static public ListBoxModel doFillCredentialItems() {
		ListBoxModel list = new ListBoxModel();

		Class<P4BaseCredentials> type = P4BaseCredentials.class;
		Jenkins scope = Jenkins.get();
		Authentication acl = ACL.SYSTEM;
		DomainRequirement domain = new DomainRequirement();

		List<P4BaseCredentials> credentials;
		credentials = CredentialsProvider.lookupCredentials(type, scope,
				acl, domain);

		if (credentials.isEmpty()) {
			list.add("Select credential...", null);
		}
		for (P4BaseCredentials c : credentials) {
			StringBuilder sb = new StringBuilder();
			sb.append(c.getDescription());
			sb.append(" (");
			sb.append(c.getUsername());
			sb.append(":");
			sb.append(c.getFullP4port());
			sb.append(")");
			list.add(sb.toString(), c.getId());
		}
		return list;
	}

	@SuppressFBWarnings(value="NP_NULL_PARAM_DEREF", justification="pending https://github.com/jenkinsci/credentials-plugin/pull/68")
	static public ListBoxModel doFillCredentialItems(Item project, String credentialsId) {

		if (project == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
				project != null && !project.hasPermission(Item.EXTENDED_READ)) {
			return new StandardListBoxModel().includeCurrentValue(credentialsId);
		}

		return new StandardListBoxModel()
				.includeEmptyValue()
				.includeMatchingAs(
						project instanceof Queue.Task
								? Tasks.getAuthenticationOf2((Queue.Task) project)
								: ACL.SYSTEM2,
						project,
						P4BaseCredentials.class,
						Collections.emptyList(),
						CredentialsMatchers.instanceOf(P4BaseCredentials.class));
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
