package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.io.Serializable;

public class ConnectionConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String p4port;
	private final boolean ssl;
	private final String serverUri;
	private final String trust;
	private final int timeout;
	private final String p4host;
	private final String userName;

	public ConnectionConfig(P4BaseCredentials credential) {
		this.p4port = credential.getFullP4port();
		this.ssl = credential.getSsl() != null;
		this.trust = credential.getTrust();
		this.serverUri = credential.getP4JavaUri();
		this.timeout = credential.getTimeout();
		this.p4host = credential.getP4host();
		this.userName = credential.getUsername();
	}

	public String getPort() {
		return p4port;
	}

	public boolean isSsl() {
		return ssl;
	}

	public String getTrust() {
		return trust;
	}

	public String getServerUri() {
		return serverUri;
	}

	public int getTimeout() {
		return timeout;
	}

	public String getP4Host() {
		return p4host;
	}

	public String getUserName() {
		return userName;
	}

	public String toString() {
		return serverUri;
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
}
