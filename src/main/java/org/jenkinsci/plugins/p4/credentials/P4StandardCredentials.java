package org.jenkinsci.plugins.p4.credentials;

import hudson.Util;

import org.kohsuke.stapler.export.Exported;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public abstract class P4StandardCredentials extends P4Credentials implements
		StandardCredentials {

	private static final long serialVersionUID = 1L;

	@NonNull
	private final String id;

	@NonNull
	private final String description;

	@NonNull
	private final String p4port;

	@NonNull
	private final TrustImpl ssl;

	@NonNull
	private final String username;

	/**
	 * Constructor.
	 * 
	 * @param id
	 *            the id.
	 * @param description
	 *            the description.
	 */
	public P4StandardCredentials(@CheckForNull String id,
			@CheckForNull String description, @CheckForNull String p4port,
			@CheckForNull TrustImpl ssl, @CheckForNull String username) {
		super();
		this.id = IdCredentials.Helpers.fixEmptyId(id);
		this.description = Util.fixNull(description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
	}

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
	public P4StandardCredentials(@CheckForNull CredentialsScope scope,
			@CheckForNull String id, @CheckForNull String description,
			@CheckForNull String p4port, @CheckForNull TrustImpl ssl,
			@CheckForNull String username) {
		super(scope);
		this.id = IdCredentials.Helpers.fixEmptyId(id);
		this.description = Util.fixNull(description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
	}

	@NonNull
	public String getDescription() {
		return description;
	}

	@NonNull
	@Exported
	public String getId() {
		return id;
	}

	@NonNull
	public String getP4port() {
		return p4port;
	}

	public boolean isSsl() {
		return (ssl == null) ? false : true;
	}

	public String getTrust() {
		return (ssl == null) ? null : ssl.getTrust();
	}

	@NonNull
	public String getUsername() {
		return username;
	}

	@Override
	public final boolean equals(Object o) {
		return IdCredentials.Helpers.equals(this, o);
	}

	@Override
	public final int hashCode() {
		return IdCredentials.Helpers.hashCode(this);
	}

	protected String defaultDescription() {
		StringBuffer sb = new StringBuffer();
		sb.append(username);
		sb.append(":");
		sb.append(p4port);
		return sb.toString();
	}
}
