package org.jenkinsci.plugins.p4.credentials;

public class P4InvalidCredentialException extends Exception {

	public P4InvalidCredentialException() {
		super("Invalid credentials");
	}
	
	public P4InvalidCredentialException(String message) {
		super("Invalid credentials: " + message);
	}
}