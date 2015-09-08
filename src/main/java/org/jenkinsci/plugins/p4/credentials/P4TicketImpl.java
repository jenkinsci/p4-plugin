package org.jenkinsci.plugins.p4.credentials;

import hudson.Extension;
import hudson.util.FormValidation;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.p4.client.ConnectionConfig;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsScope;

import edu.umd.cs.findbugs.annotations.CheckForNull;

public class P4TicketImpl extends P4BaseCredentials {

	/**
	 * Ensure consistent serialisation.
	 */
	private static final long serialVersionUID = 1L;

	@CheckForNull
	private final TicketModeImpl ticket;

	@DataBoundConstructor
	public P4TicketImpl(CredentialsScope scope, String id, String description,
			@CheckForNull String p4port, TrustImpl ssl,
			@CheckForNull String username, @CheckForNull String retry,
			TicketModeImpl ticket) {

		super(scope, id, description, p4port, ssl, username, retry);
		this.ticket = ticket;
	}

	@CheckForNull
	public String getTicketValue() {
		return ticket.getTicketValue();
	}

	public boolean isTicketValueSet() {
		return ticket.isTicketValueSet();
	}

	@CheckForNull
	public String getTicketPath() {
		return ticket.getTicketPath();
	}

	public boolean isTicketPathSet() {
		return ticket.isTicketPathSet();
	}

	@Extension
	public static class DescriptorImpl extends
			BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Ticket Credential";
		}

		public FormValidation doCheckP4port(@QueryParameter String value) {
			if (value != null && value.startsWith("ssl:")) {
				return FormValidation
						.error("Do not prefix P4PORT with 'ssl:', use the SSL checkbox.");
			}
			return FormValidation.ok();
		}

		public FormValidation doTestConnection(
				@QueryParameter("p4port") String p4port,
				@QueryParameter("ssl") String ssl,
				@QueryParameter("trust") String trust,
				@QueryParameter("username") String username,
				@QueryParameter("retry") String retry,
				@QueryParameter("ticket") String value,
				@QueryParameter("ticketValue") String ticketValue,
				@QueryParameter("ticketPath") String ticketPath)
				throws IOException, ServletException {
			try {
				// Test connection path to Server
				ConnectionConfig config = new ConnectionConfig(p4port,
						"true".equals(ssl), trust);
				FormValidation validation;
				validation = ConnectionFactory.testConnection(config);
				if (!FormValidation.ok().equals(validation)) {
					return validation;
				}

				// Test an open connection
				TrustImpl sslTrust;
				sslTrust = ("true".equals(ssl)) ? new TrustImpl(trust) : null;

				TicketModeImpl ticket;
				ticket = new TicketModeImpl(value, ticketValue, ticketPath);

				P4TicketImpl test = new P4TicketImpl(null, null, null, p4port,
						sslTrust, username, retry, ticket);

				ConnectionHelper p4 = new ConnectionHelper(test);

				if (!p4.isConnected()) {
					return FormValidation.error("Server Connection Error.");
				}

				// Test authentication
				// Do not logout, before test (preserve tickets)
				if (!p4.login()) {
					return FormValidation
							.error("Authentication Error: Unable to login.");
				}

				// Test minimum server version
				if (!p4.checkVersion(20121)) {
					return FormValidation
							.error("Server version is too old (min 2012.1)");
				}
				return FormValidation.ok("Success");
			} catch (Exception e) {
				return FormValidation.error("Connection Error: "
						+ e.getMessage());
			}
		}
	}
}