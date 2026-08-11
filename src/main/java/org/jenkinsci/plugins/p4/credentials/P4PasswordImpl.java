package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionConfig;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serial;

public class P4PasswordImpl extends P4BaseCredentials implements P4Password {

	/**
	 * Ensure consistent serialisation.
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	@NonNull
	private final Secret password;

	private boolean allhosts;

	@DataBoundConstructor
	public P4PasswordImpl(CredentialsScope scope, String id, String description, @NonNull String p4port,
	                      TrustImpl ssl, @NonNull String username, @CheckForNull String retry,
	                      @CheckForNull String timeout, @CheckForNull String p4host, @NonNull String password) {

		super(scope, id, description, p4port, ssl, username, retry, timeout, p4host);
		this.password = Secret.fromString(password);
	}

	@DataBoundSetter
	public void setAllhosts(boolean allhosts) {
		this.allhosts = allhosts;
	}

	@NonNull
	public Secret getPassword() {
		return password;
	}

	public boolean isAllhosts() {
		return allhosts;
	}

	@Extension
	@Symbol("password")
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@NonNull
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

		@POST
		public FormValidation doTestConnection(@QueryParameter("p4port") String p4port,
		                                       @QueryParameter("ssl") String ssl,
		                                       @QueryParameter("trust") String trust,
		                                       @QueryParameter("p4host") String p4host,
		                                       @QueryParameter("username") String username,
		                                       @QueryParameter("password") String password,
		                                       @QueryParameter("allhosts") boolean allhosts) {

			if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
				return FormValidation.warning("Insufficient permissions");
			}

			try {
				// Test connection path to Server
				TrustImpl sslTrust = ("true".equals(ssl)) ? new TrustImpl(trust) : null;
				P4PasswordImpl test = new P4PasswordImpl(null, null, null, p4port, sslTrust, username, null, null, p4host, password);
				test.setAllhosts(allhosts);

				ConnectionConfig config = new ConnectionConfig(test);
				FormValidation validation = ConnectionFactory.testConnection(config);
				if (!FormValidation.ok().equals(validation)) {
					return validation;
				}

				// Test an open connection
				ConnectionHelper p4 = new ConnectionHelper(test);

				if (!p4.isConnected()) {
					return FormValidation.error("Server Connection Error.");
				}

				// Test authentication
				p4.logout(); // invalidate any earlier ticket before test.
				try {
					if (!p4.login()) {
						return FormValidation.error("Authentication Error: Unable to login.");
					}
				} catch (Exception e) {
					return FormValidation.error("Authentication Error: " + e.getMessage());
				}

				// Test minimum server version
				if (!p4.checkVersion(20121)) {
					return FormValidation.error("Server version is too old (min 2012.1)");
				}
				return FormValidation.ok("Success");
			} catch (Exception e) {
				return FormValidation.error("Connection Error: " + e.getMessage());
			}
		}
	}
}
