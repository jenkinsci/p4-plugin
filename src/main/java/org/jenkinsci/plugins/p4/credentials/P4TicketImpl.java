package org.jenkinsci.plugins.p4.credentials;

import hudson.Extension;
import hudson.util.FormValidation;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsScope;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public class P4TicketImpl extends P4StandardCredentials {

	/**
	 * Ensure consistent serialisation.
	 */
	private static final long serialVersionUID = 1L;

	@NonNull
	private final TicketModeImpl ticket;

	@DataBoundConstructor
	public P4TicketImpl(@CheckForNull CredentialsScope scope,
			@CheckForNull String id, @CheckForNull String description,
			@CheckForNull String p4port, TrustImpl ssl,
			@CheckForNull String username, TicketModeImpl ticket) {

		super(scope, id, description, p4port, ssl, username);
		this.ticket = ticket;

	}

	@NonNull
	public String getTicketValue() {
		return ticket.getTicketValue();
	}

	public boolean isTicketValueSet() {
		return ticket.isTicketValueSet();
	}

	@NonNull
	public String getTicketPath() {
		return ticket.getTicketPath();
	}

	public boolean isTicketPathSet() {
		return ticket.isTicketPathSet();
	}

	@Extension
	public static class DescriptorImpl extends P4CredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Ticket Credential";
		}

		public FormValidation doTestConnection(
				@QueryParameter("p4port") String p4port,
				@QueryParameter("ssl") String ssl,
				@QueryParameter("trust") String trust,
				@QueryParameter("username") String username,
				@QueryParameter("ticket") String value,
				@QueryParameter("ticketValue") String ticketValue,
				@QueryParameter("ticketPath") String ticketPath)
				throws IOException, ServletException {
			try {
				TrustImpl sslTrust;
				sslTrust = ("true".equals(ssl)) ? new TrustImpl(trust) : null;

				TicketModeImpl ticket;
				ticket = new TicketModeImpl(value, ticketValue, ticketPath);

				P4TicketImpl test = new P4TicketImpl(null, null, null, p4port,
						sslTrust, username, ticket);

				ConnectionHelper p4 = new ConnectionHelper(test);
				
				if (!p4.isConnected()) {
					return FormValidation.error("Server Connection Error.");
				}
				p4.logout(); // invalidate any earlier ticket before test.
				if (!p4.login()) {
					return FormValidation
							.error("Authentication Error: Unable to login.");
				}
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