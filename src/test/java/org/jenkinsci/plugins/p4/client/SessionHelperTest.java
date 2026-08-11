package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.AbortException;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WithJenkins
class SessionHelperTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-SessionHelperTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeAll
	static void beforeAll(JenkinsRule rule) {
		jenkins = rule;
	}

	@BeforeEach
	void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testConnectionRetryGivesUpAndThrowsAfterExhaustingRetries() throws Exception {
		P4PasswordImpl badCredential = new P4PasswordImpl(CredentialsScope.GLOBAL, "badConnectionCred",
				"desc", "rsh:/bin/false", null, "jenkins", "0", "0", null, "jenkins");
		SystemCredentialsProvider.getInstance().getCredentials().add(badCredential);
		SystemCredentialsProvider.getInstance().save();

		AbortException thrown = assertThrows(AbortException.class,
				() -> new ConnectionHelper(badCredential, null));
		assertNotNull(thrown.getMessage());
	}

	@Test
	void testSessionCacheAvoidsRepeatedLoginOnSecondConnection() throws Exception {
		P4PasswordImpl sessionCredential = createCredentials("jenkins", "jenkins", p4d.getRshPort(),
				"sessionEnabledCred");
		sessionCredential.setSessionEnabled(true);
		sessionCredential.setSessionLife(60_000L);

		try (ConnectionHelper first = new ConnectionHelper(sessionCredential, null)) {
			assertNotNull(first.getTicket());
		}

		long loginCountBeforeSecondConnection = countOccurrences(p4d.getLogPath(), "user-login");

		try (ConnectionHelper second = new ConnectionHelper(sessionCredential, null)) {
			assertNotNull(second.getTicket());
		}

		long loginCountAfterSecondConnection = countOccurrences(p4d.getLogPath(), "user-login");
		assertEquals(loginCountBeforeSecondConnection, loginCountAfterSecondConnection,
				"a second connection with a cached session should not issue any new 'user-login' command");
	}

	private long countOccurrences(String logPath, String lookFor) throws Exception {
		long count = 0;
		try (Scanner scanner = new Scanner(new File(logPath))) {
			while (scanner.hasNextLine()) {
				if (scanner.nextLine().contains(lookFor)) {
					count++;
				}
			}
		}
		return count;
	}
}
