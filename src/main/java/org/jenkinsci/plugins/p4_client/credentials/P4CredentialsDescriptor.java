package org.jenkinsci.plugins.p4_client.credentials;

import hudson.util.FormValidation;

import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;

public abstract class P4CredentialsDescriptor extends CredentialsDescriptor {

	public FormValidation doCheckP4port(@QueryParameter String value) {
		if (value != null && value.startsWith("ssl:")) {
			return FormValidation
					.error("Do not prefix P4PORT with 'ssl:', use the SSL checkbox.");
		}
		return FormValidation.ok();
	}
}
