package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.tasks.BuildStepMonitor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApproveNotifierTest {

	@Test
	void testSimpleAccessorsAndMonitor() {
		ApproveNotifier notifier = new ApproveNotifier("cred", "review-1", "REVIEW");
		notifier.setDescription("desc");

		assertEquals("cred", notifier.getCredential());
		assertEquals("review-1", notifier.getReview());
		assertEquals("REVIEW", notifier.getStatus());
		assertEquals("desc", notifier.getDescription());
		assertEquals(BuildStepMonitor.NONE, notifier.getRequiredMonitorService());
	}

	@Test
	void testApproveReviewReturnsFalseForUnknownStatus() throws Exception {
		ApproveNotifier notifier = new ApproveNotifier("cred", "12345", "not-a-real-state");
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		EnvVars env = new EnvVars();

		try (MockedConstruction<SwarmHelper> mocked = mockConstruction(SwarmHelper.class)) {
			boolean result = notifier.approveReview(p4, env);

			assertFalse(result);
			assertEquals(1, mocked.constructed().size());
			verify(p4).log("Unknown Swarm review state: not-a-real-state");
		}
	}

	@Test
	void testApproveReviewExpandsReviewAndDescription() throws Exception {
		ApproveNotifier notifier = new ApproveNotifier("cred", "review-${BUILD_NUMBER}", "APPROVED");
		notifier.setDescription("Build ${BUILD_NUMBER} passed");

		ConnectionHelper p4 = mock(ConnectionHelper.class);
		EnvVars env = new EnvVars();
		env.put("BUILD_NUMBER", "42");

		Jenkins jenkins = mock(Jenkins.class);
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class);
			 MockedConstruction<SwarmHelper> mocked = mockConstruction(SwarmHelper.class, (mock, context) ->
					when(mock.approveReview("review-42", ApproveState.APPROVED, "Build 42 passed")).thenReturn(true))) {
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			boolean result = notifier.approveReview(p4, env);

			assertTrue(result);
		}
	}

	@Test
	void testApproveReviewWithoutDescription() throws Exception {
		ApproveNotifier notifier = new ApproveNotifier("cred", "review-1", "REVIEW");
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		EnvVars env = new EnvVars();

		Jenkins jenkins = mock(Jenkins.class);
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class);
			 MockedConstruction<SwarmHelper> mocked = mockConstruction(SwarmHelper.class, (mock, context) ->
					when(mock.approveReview("review-1", ApproveState.REVIEW, null)).thenReturn(false))) {
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			boolean result = notifier.approveReview(p4, env);

			assertFalse(result);
		}
	}
}
