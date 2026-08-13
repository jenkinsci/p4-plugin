package org.jenkinsci.plugins.p4.heal.patch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchApplierTest {

	private static final String ORIGINAL =
			"package a;\n"
					+ "\n"
					+ "class Foo {\n"
					+ "\tint bar() {\n"
					+ "\t\treturn 1;\n"
					+ "\t}\n"
					+ "}\n";

	private static FileDiff diffOf(String text) throws Exception {
		return UnifiedDiff.parse(text).getFiles().get(0);
	}

	private static String patch(String... body) {
		StringBuilder text = new StringBuilder("--- a/Foo.java\n+++ b/Foo.java\n");
		for (String line : body) {
			text.append(line).append('\n');
		}
		return text.toString();
	}

	@Test
	void testReplacesALine() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -5,1 +5,1 @@",
				"-\t\treturn 1;",
				"+\t\treturn 2;")));

		assertTrue(result.contains("\t\treturn 2;"), result);
		assertTrue(!result.contains("return 1;"), result);
	}

	@Test
	void testPreservesEverythingOutsideTheHunk() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -5,1 +5,1 @@",
				"-\t\treturn 1;",
				"+\t\treturn 2;")));

		assertEquals("package a;\n\nclass Foo {\n\tint bar() {\n\t\treturn 2;\n\t}\n}\n", result);
	}

	@Test
	void testInsertsLines() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -4,1 +4,2 @@",
				" \tint bar() {",
				"+\t\tint unused = 0;")));

		assertTrue(result.contains("\tint bar() {\n\t\tint unused = 0;\n\t\treturn 1;"), result);
	}

	@Test
	void testDeletesLines() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -2,1 +2,0 @@",
				"-")));

		assertEquals("package a;\nclass Foo {\n\tint bar() {\n\t\treturn 1;\n\t}\n}\n", result);
	}

	@Test
	void testAppliesSeveralHunks() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -1,1 +1,1 @@",
				"-package a;",
				"+package b;",
				"@@ -5,1 +5,1 @@",
				"-\t\treturn 1;",
				"+\t\treturn 7;")));

		assertTrue(result.startsWith("package b;"), result);
		assertTrue(result.contains("return 7;"), result);
	}

	@Test
	void testKeepsTabsExactly() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -4,1 +4,1 @@",
				"-\tint bar() {",
				"+\tlong bar() {")));

		assertTrue(result.contains("\tlong bar() {"), "indentation must survive verbatim");
		assertTrue(!result.contains("    long bar"), "tabs must not become spaces");
	}

	@Test
	void testKeepsTheTrailingNewline() throws Exception {
		String result = PatchApplier.apply(ORIGINAL, diffOf(patch(
				"@@ -5,1 +5,1 @@",
				"-\t\treturn 1;",
				"+\t\treturn 2;")));

		assertTrue(result.endsWith("}\n"), "trailing newline must be preserved");
	}

	@Test
	void testRejectsAContextLineThatDoesNotMatch() throws Exception {
		FileDiff diff = diffOf(patch(
				"@@ -4,2 +4,2 @@",
				" \tint baz() {",
				"-\t\treturn 1;",
				"+\t\treturn 2;"));

		PatchFormatException thrown =
				assertThrows(PatchFormatException.class, () -> PatchApplier.apply(ORIGINAL, diff));
		assertTrue(thrown.getMessage().contains("4"), thrown.getMessage());
	}

	@Test
	void testRejectsARemovedLineThatDoesNotMatch() throws Exception {
		FileDiff diff = diffOf(patch(
				"@@ -5,1 +5,1 @@",
				"-\t\treturn 99;",
				"+\t\treturn 2;"));

		assertThrows(PatchFormatException.class, () -> PatchApplier.apply(ORIGINAL, diff));
	}

	@Test
	void testRejectsAHunkPastTheEndOfTheFile() throws Exception {
		FileDiff diff = diffOf(patch(
				"@@ -400,1 +400,1 @@",
				"-nope",
				"+yes"));

		assertThrows(PatchFormatException.class, () -> PatchApplier.apply(ORIGINAL, diff));
	}

	@Test
	void testRejectsOverlappingHunks() throws Exception {
		FileDiff diff = diffOf(patch(
				"@@ -4,2 +4,2 @@",
				" \tint bar() {",
				"-\t\treturn 1;",
				"+\t\treturn 2;",
				"@@ -4,1 +4,1 @@",
				"-\tint bar() {",
				"+\tlong bar() {"));

		assertThrows(PatchFormatException.class, () -> PatchApplier.apply(ORIGINAL, diff));
	}

	@Test
	void testAppliesToFileWithoutTrailingNewline() throws Exception {
		String original = "one\ntwo";
		String result = PatchApplier.apply(original, diffOf(patch(
				"@@ -2,1 +2,1 @@",
				"-two",
				"+three")));

		assertEquals("one\nthree", result);
	}
}
