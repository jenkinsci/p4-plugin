package org.jenkinsci.plugins.p4.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * No JenkinsRule: the JVM has no Jenkins singleton, exactly like an agent JVM.
 */
class AgentGlobalSettingsUnitTest {

	@Test
	void getP4ScmReturnsNullWithoutJenkins() {
		SessionHelper helper = mock(SessionHelper.class, CALLS_REAL_METHODS);
		assertNull(assertDoesNotThrow(helper::getP4SCM));
	}
}
