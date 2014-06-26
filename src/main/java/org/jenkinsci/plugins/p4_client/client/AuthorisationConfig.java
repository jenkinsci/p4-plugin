package org.jenkinsci.plugins.p4_client.client;

import hudson.util.Secret;

import java.io.Serializable;

import org.jenkinsci.plugins.p4_client.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4_client.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4_client.credentials.P4TicketImpl;

public class AuthorisationConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	private String username;
	private AuthorisationType type;
	private Secret password;
	private String ticketValue;
	private String ticketPath;
	private String client;

	public AuthorisationConfig(P4StandardCredentials credential) {
		if (credential instanceof P4PasswordImpl) {
			P4PasswordImpl p = (P4PasswordImpl) credential;
			this.type = AuthorisationType.PASSWORD;
			this.username = p.getUsername();
			this.password = p.getPassword();
		}

		if (credential instanceof P4TicketImpl) {
			P4TicketImpl t = (P4TicketImpl) credential;
			this.type = AuthorisationType.TICKET;
			this.username = t.getUsername();

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

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ConnectionConfig) {
			ConnectionConfig comp = (ConnectionConfig) obj;
			return this.toString().equals(comp.toString());
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = (int) (1777 * hash + this.toString().hashCode());
		return hash;
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
}
