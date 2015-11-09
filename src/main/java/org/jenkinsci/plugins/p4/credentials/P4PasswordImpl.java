package org.jenkinsci.plugins.p4.credentials;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.p4.client.ConnectionConfig;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsScope;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;

public class P4PasswordImpl extends P4BaseCredentials {

	/**
	 * Ensure consistent serialisation.
	 */
	private static final long serialVersionUID = 1L;

	@CheckForNull
	private final Secret password;

	@DataBoundConstructor
	public P4PasswordImpl(CredentialsScope scope, String id, String description, @CheckForNull String p4port,
			TrustImpl ssl, @CheckForNull String username, @CheckForNull String retry, @CheckForNull String timeout,
			@CheckForNull String password) {

		super(scope, id, description, p4port, ssl, username, retry, timeout);
		this.password = Secret.fromString(password);
	}

	@CheckForNull
	public Secret getPassword() {
		return password;
	}

	@Extension
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Password Credential";
		}

		public FormValidation doCheckP4port(@QueryParameter String value) {
			if (value != null && value.startsWith("ssl:")) {
				return FormValidation.error("Do not prefix P4PORT with 'ssl:', use the SSL checkbox.");
			}
			return FormValidation.ok();
		}

		public FormValidation doTestConnection(@QueryParameter("p4port") String p4port,
				@QueryParameter("ssl") String ssl, @QueryParameter("trust") String trust,
				@QueryParameter("username") String username, @QueryParameter("retry") String retry,
				@QueryParameter("timeout") String timeout, @QueryParameter("password") String password)
						throws IOException, ServletException {
			try {
				// Test connection path to Server
				ConnectionConfig config = new ConnectionConfig(p4port, "true".equals(ssl), trust);
				FormValidation validation;
				validation = ConnectionFactory.testConnection(config);
				if (!FormValidation.ok().equals(validation)) {
					return validation;
				}

				// Test an open connection
				TrustImpl sslTrust;
				sslTrust = ("true".equals(ssl)) ? new TrustImpl(trust) : null;

				P4PasswordImpl test = new P4PasswordImpl(null, null, null, p4port, sslTrust, username, retry, timeout,
						password);

				ConnectionHelper p4 = new ConnectionHelper(test);

				if (!p4.isConnected()) {
					return FormValidation.error("Server Connection Error.");
				}

				// Test authentication
				p4.logout(); // invalidate any earlier ticket before test.
				if (!p4.login()) {
					return FormValidation.error("Authentication Error: Unable to login.");
				}

				// Test minimum server version
				if (!p4.checkVersion(20121)) {
					return FormValidation.error("Server version is too old (min 2012.1)");
				}
				return FormValidation.ok("Success");
			} catch (Exception e) {
				return FormValidation.error("Connection Error.");
			}
		}
	}
}
