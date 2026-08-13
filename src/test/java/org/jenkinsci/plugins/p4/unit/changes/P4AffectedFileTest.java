package org.jenkinsci.plugins.p4.unit.changes;

import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import hudson.scm.EditType;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P4AffectedFileTest {

	@Test
	void testConstructorFromStringsAndFileAction() {
		P4AffectedFile file = new P4AffectedFile("//depot/main/file.txt", "#3", FileAction.EDIT);

		assertEquals("//depot/main/file.txt", file.getPath());
		assertEquals("#3", file.getRevision());
		assertEquals(EditType.EDIT, file.getEditType());
		assertEquals("EDIT", file.getAction());
	}

	@Test
	void testConstructorFromFileSpecDerivesPathRevisionAndAction() throws Exception {
		IFileSpec spec = mock(IFileSpec.class);
		when(spec.getDepotPathString()).thenReturn("//depot/main/file.txt");
		when(spec.getEndRevision()).thenReturn(5);
		when(spec.getAction()).thenReturn(FileAction.ADD);

		P4AffectedFile file = new P4AffectedFile(spec);

		assertEquals("//depot/main/file.txt", file.getPath());
		assertEquals("#5", file.getRevision());
		assertEquals(EditType.ADD, file.getEditType());
		assertEquals("ADD", file.getAction());
	}

	@Test
	void testParseFileActionMapsEveryFileActionToADistinctEditType() {
		for (FileAction fileAction : FileAction.values()) {
			P4AffectedFile file = new P4AffectedFile("//depot/main/file.txt", "#1", fileAction);

			EditType type = file.getEditType();

			assertNotNull(type, "no EditType mapped for " + fileAction);
		}
	}

	@Test
	void testParseFileActionMapsKnownActionsToCoreEditTypes() {
		assertEquals(EditType.ADD, new P4AffectedFile("//depot/f", "#1", FileAction.ADD).getEditType());
		assertEquals(EditType.ADD, new P4AffectedFile("//depot/f", "#1", FileAction.ADDED).getEditType());
		assertEquals(EditType.DELETE, new P4AffectedFile("//depot/f", "#1", FileAction.DELETE).getEditType());
		assertEquals(EditType.DELETE, new P4AffectedFile("//depot/f", "#1", FileAction.DELETED).getEditType());
		assertEquals(EditType.EDIT, new P4AffectedFile("//depot/f", "#1", FileAction.EDIT_FROM).getEditType());
		assertEquals(EditType.EDIT, new P4AffectedFile("//depot/f", "#1", FileAction.EDIT_IGNORED).getEditType());
	}
}
