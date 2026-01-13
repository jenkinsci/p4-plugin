package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionConfig;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serial;

public class P4TicketImpl extends P4BaseCredentials implements P4Ticket {

	/**
	 * Ensure consistent serialisation.
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	@CheckForNull
	private final TicketModeImpl ticket;

	@DataBoundConstructor
	public P4TicketImpl(CredentialsScope scope, String id, String description, @NonNull String p4port,
	                    TrustImpl ssl, @NonNull String username, @CheckForNull String retry,
	                    @CheckForNull String timeout, @CheckForNull String p4host, TicketModeImpl ticket) {

		super(scope, id, description, p4port, ssl, username, retry, timeout, p4host);
		this.ticket = ticket;
	}

	@CheckForNull
	public String getTicketValue() {
		return (ticket == null) ? "" : ticket.getTicketValue();
	}

	public boolean isTicketValueSet() {
		return ticket != null && ticket.isTicketValueSet();
	}

	@CheckForNull
	public String getTicketPath() {
		return (ticket == null) ? "" : ticket.getTicketPath();
	}

	public boolean isTicketPathSet() {
		return ticket != null && ticket.isTicketPathSet();
	}

	@Extension
	@Symbol("ticket")
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce Ticket Credential";
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
		                                       @QueryParameter("username") String username,
		                                       @QueryParameter("p4host") String p4host,
		                                       @QueryParameter("ticket") String value,
		                                       @QueryParameter("ticketValue") String ticketValue,
		                                       @QueryParameter("ticketPath") String ticketPath) {

			if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
				return FormValidation.warning("Insufficient permissions");
			}

			try {
				// Test connection path to Server
				TrustImpl sslTrust = ("true".equals(ssl)) ? new TrustImpl(trust) : null;
				TicketModeImpl ticket = new TicketModeImpl(value, ticketValue, ticketPath);
				P4TicketImpl test = new P4TicketImpl(null, null, null, p4port, sslTrust, username, null, null, p4host, ticket);

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
				// Do not logout, before test (preserve tickets)
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