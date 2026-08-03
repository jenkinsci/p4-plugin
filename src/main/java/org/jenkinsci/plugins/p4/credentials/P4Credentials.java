package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

public interface P4Credentials extends StandardUsernameCredentials {

	/**
	 * @return p4port in the form 'hostname:port'
	 *
	 * Only used for Jelly binding; use getFullP4port()
	 */
	String getP4port();

	/**
	 * @return SSL object if enabled
	 */
	TrustImpl getSsl();

	/**
	 * @return true if SSL is selected
	 * Do not use isSsl() JENKINS-58249
	 */
	boolean isSslEnabled();

	/**
	 * @return The P4 trust fingerprint
	 */
	String getTrust();
}
