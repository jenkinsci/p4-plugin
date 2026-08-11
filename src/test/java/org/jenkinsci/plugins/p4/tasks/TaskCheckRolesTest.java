package org.jenkinsci.plugins.p4.tasks;

import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A null credential short-circuits ConnectionHelper.findCredential before it ever touches
 * the Run, so these tasks can be constructed without any real Jenkins instance or p4d - only
 * used here to reach the small role-checking/accessor methods that are otherwise only
 * exercised indirectly (with a real credential) via the p4d-backed checkout/polling tests.
 */
class TaskCheckRolesTest {

	@Test
	void testPollTaskCheckRolesChecksSlaveRole() {
		PollTask task = new PollTask(null, mock(Run.class), mock(TaskListener.class), null, new ArrayList<>());
		RoleChecker checker = mock(RoleChecker.class);

		task.checkRoles(checker);

		verify(checker).check(eq(task), eq(Roles.SLAVE));
	}

	@Test
	void testPollTaskGetPollRefChanges() {
		PollTask task = new PollTask(null, mock(Run.class), mock(TaskListener.class), null, new ArrayList<>());
		List<P4PollRef> refs = List.of();

		task.setPollRefChanges(refs);

		assertEquals(refs, task.getPollRefChanges());
	}

	@Test
	void testCheckoutTaskCheckRolesChecksSlaveRole() {
		CheckoutTask task = new CheckoutTask(null, mock(Run.class), mock(TaskListener.class), new AutoCleanImpl());
		RoleChecker checker = mock(RoleChecker.class);

		task.checkRoles(checker);

		verify(checker).check(eq(task), eq(Roles.SLAVE));
	}
}
