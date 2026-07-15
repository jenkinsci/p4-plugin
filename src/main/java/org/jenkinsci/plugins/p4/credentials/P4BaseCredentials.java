package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serial;

public abstract class P4BaseCredentials extends BaseStandardCredentials implements P4Credentials {

	@Serial
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

	@CheckForNull
	private final String p4host;

	@CheckForNull
	private String tick;

	@DataBoundSetter
	public void setTick(String tick) {
		this.tick = tick;
	}

	private boolean sessionEnabled;

	@DataBoundSetter
	public void setSessionEnabled(boolean sessionEnabled) {
		this.sessionEnabled = sessionEnabled;
	}

	private long sessionLife;

	@DataBoundSetter
	public void setSessionLife(long sessionLife) {
		this.sessionLife = sessionLife;
	}

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
	 * @param p4host      Perforce HOST (optional)
	 */
	public P4BaseCredentials(CredentialsScope scope, String id,
	                         String description, @NonNull String p4port,
	                         @CheckForNull TrustImpl ssl, @NonNull String username,
	                         @CheckForNull String retry, @CheckForNull String timeout,
	                         @CheckForNull String p4host) {
		super(scope, id, description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
		this.retry = retry;
		this.timeout = timeout;
		this.p4host = p4host;
	}

	@NonNull
	public String getP4port() {
		return p4port;
	}

	/**
	 * @return p4port including 'ssl:' if set JENKINS-62253
	 */
	public String getFullP4port() {
		if(ssl == null) {
			return p4port;
		}
		return "ssl:" + p4port;
	}

	public String getP4JavaUri() {

		if (ssl != null) {
			return "p4javassl://" + p4port;
		}
		if (p4port.startsWith("rsh:")) {
			String trim = p4port.substring(4);
			return "p4jrsh://" + trim + " --java";
		}
		return "p4java://" + p4port;
	}

	public TrustImpl getSsl() {
		return ssl;
	}

    public boolean isSslEnabled() {
	    return ssl != null;
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

	public String getP4host() {
		return (p4host == null) ? "" : p4host;
	}

	public int getTick() {
		if (tick != null && !tick.isEmpty()) {
			return Integer.parseInt(tick);
		} else {
			return 0;
		}
	}

	public boolean isSessionEnabled() {
		return sessionEnabled;
	}

	public long getSessionLife() {
		return sessionLife;
	}
}
