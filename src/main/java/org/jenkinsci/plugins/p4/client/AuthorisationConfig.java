package org.jenkinsci.plugins.p4.client;

import hudson.util.Secret;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.credentials.P4TicketImpl;

import java.io.Serializable;

public class AuthorisationConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	private String username;
	private AuthorisationType type;
	private Secret password;
	private boolean allhosts;
	private String ticketValue;
	private String ticketPath;
	private String client;

	public AuthorisationConfig(P4BaseCredentials credential) {
		if (credential instanceof P4PasswordImpl) {
			P4PasswordImpl p = (P4PasswordImpl) credential;
			this.type = AuthorisationType.PASSWORD;
			this.username = p.getUsername();
			this.password = p.getPassword();
			this.allhosts = p.isAllhosts();
		}

		if (credential instanceof P4TicketImpl) {
			P4TicketImpl t = (P4TicketImpl) credential;
			this.type = AuthorisationType.TICKETPATH;
			this.username = t.getUsername();
			this.allhosts = false;

			if (t.isTicketValueSet()) {
				this.type = AuthorisationType.TICKET;
				this.ticketValue = t.getTicketValue();
			}
			if (t.isTicketPathSet()) {
				this.type = AuthorisationType.TICKETPATH;
				this.ticketPath = t.getTicketPath();
			}
		}

	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(username);
		sb.append((client != null) ? "@" + client : "@no-client");
		return sb.toString();
	}

	public String getClient() {
		return client;
	}

	public void setClient(String client) {
		this.client = client;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password.getPlainText();
	}

	public AuthorisationType getType() {
		return type;
	}

	public String getTicketValue() {
		return ticketValue;
	}

	public String getTicketPath() {
		return ticketPath;
	}

	public boolean isAllhosts() {
		return allhosts;
	}
}
