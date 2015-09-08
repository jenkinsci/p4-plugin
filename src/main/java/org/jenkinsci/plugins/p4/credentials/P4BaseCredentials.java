package org.jenkinsci.plugins.p4.credentials;

import hudson.Util;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;

public abstract class P4BaseCredentials extends BaseStandardCredentials {

	private static final long serialVersionUID = 1L;

	@CheckForNull
	private final String p4port;

	@CheckForNull
	private final TrustImpl ssl;

	@CheckForNull
	private final String username;

	@CheckForNull
	private final String retry;

	/**
	 * Constructor.
	 * 
	 * @param scope
	 *            the scope.
	 * @param id
	 *            the id.
	 * @param description
	 *            the description.
	 */
	public P4BaseCredentials(CredentialsScope scope, String id,
			String description, @CheckForNull String p4port,
			@CheckForNull TrustImpl ssl, @CheckForNull String username,
			@CheckForNull String retry) {
		super(scope, id, description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
		this.retry = retry;
	}

	@CheckForNull
	public String getP4port() {
		return p4port;
	}

	public boolean isSsl() {
		return (ssl == null) ? false : true;
	}

	@CheckForNull
	public String getTrust() {
		return (ssl == null) ? null : ssl.getTrust();
	}

	@CheckForNull
	public String getUsername() {
		return username;
	}

	public int getRetry() {
		if (retry != null && !retry.isEmpty()) {
			return Integer.parseInt(retry);
		} else {
			return 0;
		}
	}
}
