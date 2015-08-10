package org.jenkinsci.plugins.p4.credentials;

import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.List;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

public class P4CredentialsImpl {
	
	static public ListBoxModel doFillCredentialItems() {
		ListBoxModel list = new ListBoxModel();

		Class<P4StandardCredentials> type = P4StandardCredentials.class;
		Jenkins scope = Jenkins.getInstance();
		Authentication acl = ACL.SYSTEM;
		DomainRequirement domain = new DomainRequirement();

		List<P4StandardCredentials> credentials;
		credentials = CredentialsProvider.lookupCredentials(type, scope,
				acl, domain);

		if (credentials.isEmpty()) {
			list.add("Select credential...", null);
		}
		for (P4StandardCredentials c : credentials) {
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
}
