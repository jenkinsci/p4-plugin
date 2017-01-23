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
	private String p4prog;
	private String p4version;

	public ConnectionConfig(P4BaseCredentials credential) {
		this.p4port = credential.getP4port();
		this.ssl = credential.isSsl();
		this.trust = credential.getTrust();
		this.serverUri = credential.getP4JavaUri();
		this.timeout = credential.getTimeout();
		this.p4host = credential.getP4host();
		this.p4prog = credential.getP4Prog();
		this.p4version = credential.getP4Version();
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

	public String getP4Host() { return p4host; }

	public String toString() {
		return serverUri;
	}

	public String getP4Prog() {
		return p4prog;
	}

	public String getP4Version() {
		return p4version;
	}

	public void setP4Version(String p4version) {
		this.p4version = p4version;
	}

	public void setP4Prog(String p4prog) {
		this.p4prog = p4prog;
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
