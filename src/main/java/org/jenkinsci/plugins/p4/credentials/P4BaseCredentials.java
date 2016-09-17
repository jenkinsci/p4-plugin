package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;

public abstract class P4BaseCredentials extends BaseStandardCredentials implements P4Credentials {

	private static final long serialVersionUID = 1L;

	@NonNull
	private final String p4port;

	@CheckForNull
	private final TrustImpl ssl;

	@NonNull
	private final String username;

	@CheckForNull
	private final String retry;

	@CheckForNull
	private final String timeout;

	/**
	 * Constructor.
	 *
	 * @param scope       the scope.
	 * @param id          the id.
	 * @param description the description.
	 * @param p4port      Perforce port
	 * @param ssl         Perforce SSL options
	 * @param username    Perforce username
	 * @param retry       Perforce connection retry option
	 * @param timeout     Perforce connection timeout option
	 */
	public P4BaseCredentials(CredentialsScope scope, String id,
	                         String description, @NonNull String p4port,
	                         @CheckForNull TrustImpl ssl, @NonNull String username,
	                         @CheckForNull String retry, @CheckForNull String timeout) {
		super(scope, id, description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
		this.retry = retry;
		this.timeout = timeout;
	}

	@NonNull
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

	@NonNull
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

	public int getTimeout() {
		if (timeout != null && !timeout.isEmpty()) {
			return Integer.parseInt(timeout);
		} else {
			return 0;
		}
	}
}
