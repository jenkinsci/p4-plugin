package org.jenkinsci.plugins.p4.client;

import java.io.Serializable;

import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

public class ConnectionConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String p4port;
	private final boolean ssl;
	private final String serverUri;
	private final String trust;

	public ConnectionConfig(P4BaseCredentials credential) {
		this.p4port = credential.getP4port();
		this.ssl = credential.isSsl();
		this.trust = credential.getTrust();
		this.serverUri = toUri();
	}

	public ConnectionConfig(String p4port, boolean ssl, String trust) {
		this.p4port = p4port;
		this.ssl = ssl;
		this.trust = trust;
		this.serverUri = toUri();
	}

	private String toUri() {
		if (ssl) {
			return "p4javassl://" + p4port;
		} else {
			return "p4java://" + p4port;
		}
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
