package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ApproveNotifierStepTest {

	@Test
	void testConstructorAndSetterDelegateToSuper() {
		ApproveNotifierStep step = new ApproveNotifierStep("cred", "review-1", "REVIEW");
		step.setDescription("desc");

		assertEquals("cred", step.getCredential());
		assertEquals("review-1", step.getReview());
		assertEquals("REVIEW", step.getStatus());
		assertEquals("desc", step.getDescription());
	}

	@Test
	void testPerformCompletesWhenApproveReviewReturnsFalse() throws Exception {
		ApproveNotifierStep step = new ApproveNotifierStep("cred", "12345", "not-a-real-state");

		Run<?, ?> run = mock(Run.class);
		FilePath workspace = mock(FilePath.class);
		Launcher launcher = mock(Launcher.class);
		TaskListener listener = mock(TaskListener.class);
		EnvVars env = new EnvVars();
		when(run.getEnvironment(listener)).thenReturn(env);

		try (MockedConstruction<ConnectionHelper> connMocked = mockConstruction(ConnectionHelper.class);
			 MockedConstruction<SwarmHelper> swarmMocked = mockConstruction(SwarmHelper.class)) {

			step.perform(run, workspace, launcher, listener);

			assertEquals(1, connMocked.constructed().size());
			assertEquals(1, swarmMocked.constructed().size());
		}
	}

	@Test
	void testPerformWrapsApproveReviewExceptionAsIOException() throws Exception {
		ApproveNotifierStep step = new ApproveNotifierStep("cred", "review-1", "REVIEW");

		Run<?, ?> run = mock(Run.class);
		FilePath workspace = mock(FilePath.class);
		Launcher launcher = mock(Launcher.class);
		TaskListener listener = mock(TaskListener.class);
		EnvVars env = new EnvVars();
		when(run.getEnvironment(listener)).thenReturn(env);

		Jenkins jenkins = mock(Jenkins.class);
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class);
			 MockedConstruction<ConnectionHelper> connMocked = mockConstruction(ConnectionHelper.class);
			 MockedConstruction<SwarmHelper> swarmMocked = mockConstruction(SwarmHelper.class, (swarmMock, context) ->
					when(swarmMock.approveReview("review-1", ApproveState.REVIEW, null)).thenThrow(new Exception("boom")))) {
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			IOException ex = assertThrows(IOException.class,
					() -> step.perform(run, workspace, launcher, listener));

			assertEquals("Unable to update Review.", ex.getMessage());
		}
	}
}
