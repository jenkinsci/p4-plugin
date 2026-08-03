package org.jenkinsci.plugins.p4.unit.changes;

import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class P4ChangeEntryTest {

	private P4ChangeEntry newEntry() {
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(null);
			return new P4ChangeEntry(null);
		}
	}

	@Test
	void testNoArgConstructorUsesDefaultFileLimit() {
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(null);
			P4ChangeEntry entry = new P4ChangeEntry();
			assertEquals(PerforceScm.DEFAULT_FILE_LIMIT, entry.getMaxLimit());
		}
	}

	@Test
	void testIdAndChangeNumber() {
		P4ChangeEntry entry = newEntry();
		P4Ref ref = new P4PollRef(42, "//depot/...");

		entry.setId(ref);

		assertEquals(ref, entry.getId());
		assertEquals("42", entry.getChangeNumber());
		assertEquals("42", entry.getCommitId());
		assertFalse(entry.isLabel());
	}

	@Test
	void testDateRoundTripAndDefensiveCopy() {
		P4ChangeEntry entry = newEntry();

		entry.setDate("2024-01-02 03:04:05");

		assertEquals("2024-01-02 03:04:05", entry.getChangeTime());
		Date first = entry.getDate();
		Date second = entry.getDate();
		assertEquals(first, second);
		assertNotSame(first, second);
		assertEquals(first.getTime(), entry.getTimestamp());
	}

	@Test
	void testClientId() {
		P4ChangeEntry entry = newEntry();
		entry.setClientId("my-client");
		assertEquals("my-client", entry.getClientId());
	}

	@Test
	void testMsg() {
		P4ChangeEntry entry = newEntry();
		entry.setMsg("hello world");
		assertEquals("hello world", entry.getMsg());
	}

	@Test
	void testGetRowsCountsLinesUpToTen() {
		P4ChangeEntry entry = newEntry();

		entry.setMsg("line1\nline2\nline3");
		assertEquals(3, entry.getRows());

		StringBuilder longMsg = new StringBuilder();
		for (int i = 0; i < 15; i++) {
			longMsg.append("line").append(i).append("\n");
		}
		entry.setMsg(longMsg.toString());
		assertEquals(10, entry.getRows());
	}

	@Test
	void testAffectedFilesAndPaths() {
		P4ChangeEntry entry = newEntry();
		P4AffectedFile file = mock(P4AffectedFile.class);
		when(file.getPath()).thenReturn("//depot/foo");

		entry.addAffectedFiles(file);

		Collection<P4AffectedFile> files = entry.getAffectedFiles();
		assertEquals(List.of(file), files);
		assertEquals(List.of("//depot/foo"), entry.getAffectedPaths());
	}

	@Test
	void testFileLimit() {
		P4ChangeEntry entry = newEntry();
		assertFalse(entry.isFileLimit());
		entry.setFileLimit(true);
		assertTrue(entry.isFileLimit());
	}

	@Test
	void testShelved() {
		P4ChangeEntry entry = newEntry();
		assertFalse(entry.isShelved());
		entry.setShelved(true);
		assertTrue(entry.isShelved());
	}

	@Test
	void testGetAction() {
		P4ChangeEntry entry = newEntry();
		IFileSpec file = mock(IFileSpec.class);
		when(file.getAction()).thenReturn(FileAction.MOVE_ADD);

		assertEquals("MOVE_ADD", entry.getAction(file));
	}

	@Test
	void testJobs() {
		P4ChangeEntry entry = newEntry();
		IFix job = mock(IFix.class);
		when(job.getStatus()).thenReturn("closed");

		entry.addJob(job);

		assertEquals(List.of(job), entry.getJobs());
		assertEquals("closed", entry.getJobStatus(job));
	}
}
