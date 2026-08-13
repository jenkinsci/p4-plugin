package org.jenkinsci.plugins.p4.heal.patch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffTest {

	private static String diff(String... lines) {
		return String.join("\n", lines) + "\n";
	}

	@Test
	void testParseSingleFileSingleHunk() throws Exception {
		String text = diff(
				"--- a/src/main/java/Foo.java",
				"+++ b/src/main/java/Foo.java",
				"@@ -10,4 +10,4 @@",
				" public class Foo {",
				" 	public void bar() {",
				"-		int x = 1;",
				"+		int x = 2;",
				" 	}");

		UnifiedDiff patch = UnifiedDiff.parse(text);

		assertEquals(1, patch.getFiles().size());
		FileDiff file = patch.getFiles().get(0);
		assertEquals("src/main/java/Foo.java", file.getPath());
		assertEquals(List.of("		int x = 2;"), file.getAddedLines());
		assertEquals(List.of("		int x = 1;"), file.getRemovedLines());
	}

	@Test
	void testParseHunkHeaderNumbers() throws Exception {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ -10,5 +12,6 @@",
				" a",
				"+b",
				" c",
				" d",
				" e",
				" f");

		Hunk hunk = UnifiedDiff.parse(text).getFiles().get(0).getHunks().get(0);

		assertEquals(10, hunk.getOldStart());
		assertEquals(List.of(" a", "+b", " c", " d", " e", " f"), hunk.getLines());
	}

	@Test
	void testParseOmittedCountDefaultsToOne() throws Exception {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ -3 +3 @@",
				"-old",
				"+new");

		Hunk hunk = UnifiedDiff.parse(text).getFiles().get(0).getHunks().get(0);

		assertEquals(3, hunk.getOldStart());
		assertEquals(List.of("-old", "+new"), hunk.getLines());
	}

	@Test
	void testParseMultipleFiles() throws Exception {
		String text = diff(
				"--- a/One.java",
				"+++ b/One.java",
				"@@ -1 +1 @@",
				"-a",
				"+b",
				"--- a/Two.java",
				"+++ b/Two.java",
				"@@ -1 +1 @@",
				"-c",
				"+d");

		UnifiedDiff patch = UnifiedDiff.parse(text);

		assertEquals(2, patch.getFiles().size());
		assertEquals("One.java", patch.getFiles().get(0).getPath());
		assertEquals("Two.java", patch.getFiles().get(1).getPath());
	}

	@Test
	void testParseMultipleHunksInOneFile() throws Exception {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ -1 +1 @@",
				"-a",
				"+b",
				"@@ -20 +20 @@",
				"-c",
				"+d");

		FileDiff file = UnifiedDiff.parse(text).getFiles().get(0);

		assertEquals(2, file.getHunks().size());
		assertEquals(List.of("b", "d"), file.getAddedLines());
		assertEquals(List.of("a", "c"), file.getRemovedLines());
	}

	@Test
	void testParseIgnoresGitPreamble() throws Exception {
		String text = diff(
				"diff --git a/Foo.java b/Foo.java",
				"index 83db48f..bf269f4 100644",
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ -1 +1 @@",
				"-a",
				"+b");

		UnifiedDiff patch = UnifiedDiff.parse(text);

		assertEquals(1, patch.getFiles().size());
		assertEquals("Foo.java", patch.getFiles().get(0).getPath());
	}

	@Test
	void testParseKeepsPathWithoutPrefix() throws Exception {
		String text = diff(
				"--- src/main/java/Foo.java",
				"+++ src/main/java/Foo.java",
				"@@ -1 +1 @@",
				"-a",
				"+b");

		assertEquals("src/main/java/Foo.java", UnifiedDiff.parse(text).getFiles().get(0).getPath());
	}

	@Test
	void testParseRejectsEmptyInput() {
		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(""));
		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse("   \n  \n"));
		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(null));
	}

	@Test
	void testParseRejectsProseWithoutDiffHeaders() {
		String text = diff(
				"Sure! Here is the fix you asked for:",
				"You should change int x = 1 to int x = 2.");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsMissingNewFileHeader() {
		String text = diff(
				"--- a/Foo.java",
				"@@ -1 +1 @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsHunkBeforeFileHeader() {
		String text = diff(
				"@@ -1 +1 @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsMalformedHunkHeader() {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ this is not a hunk header @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsFileWithNoHunks() {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsHunkWhoseLineCountsDisagreeWithHeader() {
		String text = diff(
				"--- a/Foo.java",
				"+++ b/Foo.java",
				"@@ -1,5 +1,5 @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsAbsolutePath() {
		String text = diff(
				"--- /etc/passwd",
				"+++ /etc/passwd",
				"@@ -1 +1 @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsPathEscapingWorkspace() {
		String text = diff(
				"--- a/../../etc/passwd",
				"+++ b/../../etc/passwd",
				"@@ -1 +1 @@",
				"-a",
				"+b");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testParseRejectsDevNullTarget() {
		String text = diff(
				"--- a/Foo.java",
				"+++ /dev/null",
				"@@ -1 +0,0 @@",
				"-a");

		assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse(text));
	}

	@Test
	void testPatchFormatExceptionCarriesReason() {
		PatchFormatException e = assertThrows(PatchFormatException.class, () -> UnifiedDiff.parse("not a diff"));
		assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
	}
}
