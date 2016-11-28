package org.jenkinsci.plugins.p4;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;

import java.io.IOException;

abstract public class DefaultEnvironment {

	protected final static String VERSION = "r15.1";
	protected final static String CREDENTIAL = "id";
	protected final static int HTTP_PORT = 1888;
	protected final static String HTTP_URL = "http://localhost:" + HTTP_PORT;
	protected final int LOG_LIMIT = 1000;

	protected P4PasswordImpl createCredentials(String user, String password, SampleServerRule p4d) throws IOException {
		String p4port = p4d.getRshPort();
		CredentialsScope scope = CredentialsScope.SYSTEM;
		P4PasswordImpl auth = new P4PasswordImpl(scope, CREDENTIAL, "desc", p4port, null, user, "0", "0", null, password);
		SystemCredentialsProvider.getInstance().getCredentials().clear();
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();
		return auth;
	}

	protected static void startHttpServer(int port) throws Exception {
		DummyServer server = new DummyServer(port);
		new Thread(server).start();
	}

	protected String defaultClient() {
		String client = "test.ws";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			client = "test.win";
		}
		return client;
	}
}
