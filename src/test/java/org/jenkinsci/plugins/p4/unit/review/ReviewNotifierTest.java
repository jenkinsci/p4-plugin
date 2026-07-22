package org.jenkinsci.plugins.p4.unit.review;

import hudson.matrix.MatrixRun;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.review.ReviewNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.PrintStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewNotifierTest {

	private final ReviewNotifier notifier = new ReviewNotifier();

	@Test
	void testOnCompletedSkipsMatrixRun() {
		MatrixRun run = mock(MatrixRun.class);
		TaskListener listener = mock(TaskListener.class);

		notifier.onCompleted(run, listener);

		verify(run, never()).getResult();
	}

	@Test
	void testOnCompletedSkipsWhenResultIsNull() {
		Run run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		when(run.getResult()).thenReturn(null);

		notifier.onCompleted(run, listener);
	}

	@Test
	void testOnCompletedCatchesEnvironmentFailure() throws Exception {
		Run run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		PrintStream logger = mock(PrintStream.class);
		when(run.getResult()).thenReturn(Result.SUCCESS);
		when(run.getEnvironment(listener)).thenThrow(new IOException("boom"));
		when(listener.getLogger()).thenReturn(logger);

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::getInstanceOrNull).thenReturn(null);

			notifier.onCompleted(run, listener);
		}

		verify(logger).println("Warning: Unable to connect to url, []. Logger can be setup for detailed analysis.");
	}

	@Test
	void testOnStartedSkipsNullOrMatrixRun() {
		TaskListener listener = mock(TaskListener.class);
		notifier.onStarted(null, listener);

		MatrixRun run = mock(MatrixRun.class);
		notifier.onStarted(run, listener);
	}

	@Test
	void testOnStartedSkipsWhenJenkinsMissing() {
		Run run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::getInstanceOrNull).thenReturn(null);
			notifier.onStarted(run, listener);
		}
	}
}
