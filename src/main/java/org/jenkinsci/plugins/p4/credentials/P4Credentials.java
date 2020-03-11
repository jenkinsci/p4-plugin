package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

public interface P4Credentials extends StandardUsernameCredentials {

	/**
	 * @return p4port in the form 'hostname:port'
	 */
	public String getP4port();

	/**
	 * @return SSL object if enabled
	 */
	public TrustImpl getSsl();

	/**
	 * @return The P4 trust fingerprint
	 */
	public String getTrust();
}
