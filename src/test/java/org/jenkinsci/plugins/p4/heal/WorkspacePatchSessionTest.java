package org.jenkinsci.plugins.p4.heal;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePatchSessionTest {

	private static final String FOO = "package a;\nclass Foo {\n\tint bar() {\n\t\treturn 1;\n\t}\n}\n";
	private static final String BAR = "package a;\nclass Bar {\n}\n";

	@TempDir
	private File temp;

	private FilePath workspace;
	private final ByteArrayOutputStream console = new ByteArrayOutputStream();
	private TaskListener listener;

	@BeforeEach
	void beforeEach() throws Exception {
		workspace = new FilePath(temp);
		listener = new StreamTaskListener(console, StandardCharsets.UTF_8);
		write("src/main/java/Foo.java", FOO);
		write("src/main/java/Bar.java", BAR);
	}

	private void write(String relative, String content) throws Exception {
		Path file = temp.toPath().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private String read(String relative) throws Exception {
		return Files.readString(temp.toPath().resolve(relative), StandardCharsets.UTF_8);
	}

	private static UnifiedDiff patch(String body) throws Exception {
		return UnifiedDiff.parse(body);
	}

	private static final String FIX_FOO =
			"--- a/src/main/java/Foo.java\n"
					+ "+++ b/src/main/java/Foo.java\n"
					+ "@@ -4,1 +4,1 @@\n"
					+ "-\t\treturn 1;\n"
					+ "+\t\treturn 2;\n";

	private static final String TOUCHES_TWO_FILES = FIX_FOO
			+ "--- a/src/main/java/Bar.java\n"
			+ "+++ b/src/main/java/Bar.java\n"
			+ "@@ -2,1 +2,1 @@\n"
			+ "-class Bar {\n"
			+ "+class Bar implements Runnable {\n";

	private WorkspacePatchSession session() {
		return new WorkspacePatchSession(workspace, listener, true, null);
	}

	@Test
	void testApplyWritesThePatchedContent() throws Exception {
		session().apply(patch(FIX_FOO));

		assertTrue(read("src/main/java/Foo.java").contains("\t\treturn 2;"));
	}

	@Test
	void testRevertRestoresTheOriginalExactly() throws Exception {
		WorkspacePatchSession session = session();

		session.apply(patch(FIX_FOO));
		session.revert();

		assertEquals(FOO, read("src/main/java/Foo.java"));
	}

	@Test
	void testRevertRestoresEveryFileThePatchTouched() throws Exception {
		WorkspacePatchSession session = session();

		session.apply(patch(TOUCHES_TWO_FILES));
		assertTrue(read("src/main/java/Bar.java").contains("Runnable"));

		session.revert();

		assertEquals(FOO, read("src/main/java/Foo.java"));
		assertEquals(BAR, read("src/main/java/Bar.java"));
	}

	@Test
	void testRevertIsSafeWhenNothingWasApplied() throws Exception {
		session().revert();

		assertEquals(FOO, read("src/main/java/Foo.java"));
	}

	@Test
	void testRevertAfterRevertIsHarmless() throws Exception {
		WorkspacePatchSession session = session();

		session.apply(patch(FIX_FOO));
		session.revert();
		session.revert();

		assertEquals(FOO, read("src/main/java/Foo.java"));
	}

	@Test
	void testFailedApplyLeavesTheWorkspaceUntouched() throws Exception {
		String stale = "--- a/src/main/java/Foo.java\n"
				+ "+++ b/src/main/java/Foo.java\n"
				+ "@@ -4,1 +4,1 @@\n"
				+ "-\t\treturn 99;\n"
				+ "+\t\treturn 2;\n";

		WorkspacePatchSession session = session();
		assertThrows(IOException.class, () -> session.apply(patch(stale)));
		session.revert();

		assertEquals(FOO, read("src/main/java/Foo.java"),
				"a hunk that did not match must not leave a half-applied file behind");
	}

	@Test
	void testPartialFailureRollsBackTheFilesAlreadyWritten() throws Exception {
		String goodThenBad = FIX_FOO
				+ "--- a/src/main/java/Bar.java\n"
				+ "+++ b/src/main/java/Bar.java\n"
				+ "@@ -2,1 +2,1 @@\n"
				+ "-class Nope {\n"
				+ "+class Bar implements Runnable {\n";

		WorkspacePatchSession session = session();
		assertThrows(IOException.class, () -> session.apply(patch(goodThenBad)));
		session.revert();

		assertEquals(FOO, read("src/main/java/Foo.java"));
		assertEquals(BAR, read("src/main/java/Bar.java"));
	}

	@Test
	void testMissingTargetFileIsReported() throws Exception {
		String ghost = "--- a/src/main/java/Ghost.java\n"
				+ "+++ b/src/main/java/Ghost.java\n"
				+ "@@ -1,1 +1,1 @@\n"
				+ "-a\n"
				+ "+b\n";

		IOException thrown = assertThrows(IOException.class, () -> session().apply(patch(ghost)));

		assertTrue(thrown.getMessage().contains("Ghost.java"), thrown.getMessage());
	}

	@Test
	void testDryRunDeliversNothingButPrintsTheDiff() throws Exception {
		WorkspacePatchSession session = session();
		session.apply(patch(FIX_FOO));

		String reference = session.deliver(patch(FIX_FOO), "AI fix: off-by-one");

		assertTrue(reference.toLowerCase(Locale.ROOT).contains("dry run"), reference);
		assertTrue(console.toString(StandardCharsets.UTF_8).contains("return 2;"),
				"the verified diff must be printed so a human can act on it");
	}

	@Test
	void testDryRunRevertsTheWorkspaceSoTheBuildIsLeftClean() throws Exception {
		WorkspacePatchSession session = session();
		session.apply(patch(FIX_FOO));
		session.deliver(patch(FIX_FOO), "AI fix");

		assertEquals(FOO, read("src/main/java/Foo.java"),
				"a dry run must not leave modified files in the workspace");
	}
}
