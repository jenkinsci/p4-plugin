package org.jenkinsci.plugins.p4.unit.tagging;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagActionTest {

	@Test
	void testGetLastPollChangeDedupsAndPreservesOrder() {
		Run<?, ?> run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		when(listener.getLogger()).thenReturn(mock(PrintStream.class));

		TagAction action1 = mock(TagAction.class);
		when(action1.getSyncID()).thenReturn("sync-1");
		P4PollRef ref1 = new P4PollRef(10, "//depot/a/...");
		P4PollRef ref2 = new P4PollRef(20, "//depot/b/...");
		when(action1.getCustomPollPathChanges()).thenReturn(new ArrayList<>(Arrays.asList(ref1, null, ref2)));

		TagAction action2 = mock(TagAction.class);
		when(action2.getSyncID()).thenReturn("sync-1");
		P4PollRef ref2Dup = new P4PollRef(20, "//depot/b/...");
		when(action2.getCustomPollPathChanges()).thenReturn(List.of(ref2Dup));

		when(run.getActions(TagAction.class)).thenReturn(List.of(action1, action2));

		List<P4PollRef> result = TagAction.getLastPollChange(run, listener, "sync-1");

		assertEquals(List.of(ref1, ref2), result);
	}

	@Test
	void testGetLastPollChangeSkipsActionsWithNoPollChanges() {
		Run<?, ?> run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		when(listener.getLogger()).thenReturn(mock(PrintStream.class));

		TagAction action = mock(TagAction.class);
		when(action.getSyncID()).thenReturn("sync-1");
		when(action.getCustomPollPathChanges()).thenReturn(Collections.emptyList());

		when(run.getActions(TagAction.class)).thenReturn(List.of(action));

		List<P4PollRef> result = TagAction.getLastPollChange(run, listener, "sync-1");

		assertTrue(result.isEmpty());
	}

	@Test
	void testGetLastChangeReturnsEmptyWhenNoPreviousActions() {
		Run<?, ?> run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		PrintStream logger = mock(PrintStream.class);
		when(listener.getLogger()).thenReturn(logger);
		when(run.getActions(TagAction.class)).thenReturn(Collections.emptyList());

		List<P4Ref> result = TagAction.getLastChange(run, listener, "sync-1");

		assertTrue(result.isEmpty());
		verify(logger).println("No previous build found...");
	}

	@Test
	void testGetLastChangeReturnsEmptyForNullSyncID() {
		Run<?, ?> run = mock(Run.class);
		TaskListener listener = mock(TaskListener.class);
		PrintStream logger = mock(PrintStream.class);
		when(listener.getLogger()).thenReturn(logger);

		List<P4Ref> result = TagAction.getLastChange(run, listener, null);

		assertTrue(result.isEmpty());
		verify(logger).println("No build found for syncID: ...null");
	}

	@Test
	void testGetLastActionReturnsLastNonNullAction() {
		Run<?, ?> run = mock(Run.class);
		TagAction action1 = mock(TagAction.class);
		TagAction action2 = mock(TagAction.class);
		when(run.getActions(TagAction.class)).thenReturn(List.of(action1, action2));
		when(run.getAction(TagAction.class)).thenReturn(action1);

		TagAction result = TagAction.getLastAction(run);

		assertEquals(action2, result);
	}

	@Test
	void testGetLastActionReturnsNullWhenNoActions() {
		Run<?, ?> run = mock(Run.class);
		when(run.getActions(TagAction.class)).thenReturn(Collections.emptyList());

		assertNull(TagAction.getLastAction(run));
	}
}
