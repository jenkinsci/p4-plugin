package org.jenkinsci.plugins.p4;

import hudson.model.Run;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4JENKINS-159: PerforceScm.findBaselineBuild() walks back from the previous build to the most recent
 * build that recorded a TagAction for the workspace's syncID (i.e. synced it), bounded by a maximum number
 * of steps.
 */
class FindBaselineBuildTest {

	private static final String SYNC_ID = "jenkins-job";

	private static Run<?, ?> build(boolean tagged) {
		return tagged ? taggedWith(SYNC_ID) : untagged();
	}

	private static Run<?, ?> taggedWith(String syncID) {
		Run<?, ?> run = mock(Run.class);
		TagAction action = mock(TagAction.class);
		when(action.getSyncID()).thenReturn(syncID);
		when(run.getActions(TagAction.class)).thenReturn(List.of(action));
		return run;
	}

	private static Run<?, ?> untagged() {
		Run<?, ?> run = mock(Run.class);
		when(run.getActions(TagAction.class)).thenReturn(Collections.emptyList());
		return run;
	}

	private static void previousCompleted(Run<?, ?> run, Run<?, ?> previous) {
		when(run.getPreviousCompletedBuild()).thenAnswer(i -> previous);
	}

	private static void previousSuccessful(Run<?, ?> run, Run<?, ?> previous) {
		when(run.getPreviousSuccessfulBuild()).thenAnswer(i -> previous);
	}

	@Test
	void nullStartHasNoBaseline() {
		assertNull(PerforceScm.findBaselineBuild(null, SYNC_ID, false, 5));
	}

	@Test
	void taggedStartIsTheBaselineWithoutWalking() {
		Run<?, ?> start = build(true);

		assertSame(start, PerforceScm.findBaselineBuild(start, SYNC_ID, false, 5));
		verify(start, never()).getPreviousCompletedBuild();
	}

	@Test
	void walksPastBuildsWithoutTagAction() {
		Run<?, ?> baseline = build(true);
		Run<?, ?> failed1 = build(false);
		Run<?, ?> failed2 = build(false);
		previousCompleted(failed1, failed2);
		previousCompleted(failed2, baseline);

		assertSame(baseline, PerforceScm.findBaselineBuild(failed1, SYNC_ID, false, 5));
	}

	@Test
	void walksPastBuildTaggedOnlyForAnotherSyncID() {
		Run<?, ?> baseline = build(true);
		Run<?, ?> otherWorkspace = taggedWith("jenkins-job-tools");
		previousCompleted(otherWorkspace, baseline);

		assertSame(baseline, PerforceScm.findBaselineBuild(otherWorkspace, SYNC_ID, false, 5));
	}

	@Test
	void baselineExactlyAtCapIsFound() {
		Run<?, ?> baseline = build(true);
		Run<?, ?> failed = build(false);
		previousCompleted(failed, baseline);

		assertSame(baseline, PerforceScm.findBaselineBuild(failed, SYNC_ID, false, 1));
	}

	@Test
	void baselineBeyondCapIsNotFound() {
		Run<?, ?> baseline = build(true);
		Run<?, ?> failed1 = build(false);
		Run<?, ?> failed2 = build(false);
		previousCompleted(failed1, failed2);
		previousCompleted(failed2, baseline);

		assertNull(PerforceScm.findBaselineBuild(failed1, SYNC_ID, false, 1));
	}

	@Test
	void exhaustedHistoryHasNoBaseline() {
		Run<?, ?> failed = build(false);
		previousCompleted(failed, null);

		assertNull(PerforceScm.findBaselineBuild(failed, SYNC_ID, false, 5));
	}

	@Test
	void sinceLastSuccessWalksSuccessfulBuilds() {
		Run<?, ?> baseline = build(true);
		Run<?, ?> untagged = build(false);
		previousSuccessful(untagged, baseline);

		assertSame(baseline, PerforceScm.findBaselineBuild(untagged, SYNC_ID, true, 5));
		verify(untagged, never()).getPreviousCompletedBuild();
	}
}
