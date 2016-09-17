package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * Bind P4Credentials with the StandardUsernamePasswordCredentials interface.
 */
public interface P4Password extends P4Credentials, StandardUsernamePasswordCredentials {

}
