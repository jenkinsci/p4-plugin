package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

public interface P4Credentials extends StandardUsernameCredentials {

	/**
	 * @return p4port in the form 'hostname:port'
	 */
	public String getP4port();

	/**
	 * @return true if SSL is selected
	 */
	public boolean isSsl();

	/**
	 * @return The P4 trust fingerprint
	 */
	public String getTrust();
}
