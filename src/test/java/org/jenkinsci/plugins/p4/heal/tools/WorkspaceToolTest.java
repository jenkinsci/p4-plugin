package org.jenkinsci.plugins.p4.heal.tools;

import hudson.FilePath;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceToolTest {

	@TempDir
	private File temp;

	private FilePath workspace;

	@BeforeEach
	void beforeEach() throws Exception {
		workspace = new FilePath(temp);
		write("src/main/java/Foo.java", "package a;\nclass Foo {\n\tint bar() {\n\t\treturn 1;\n\t}\n}\n");
		write("src/main/java/Bar.java", "package a;\nclass Bar {\n\tvoid callsBar() {\n\t\tnew Foo().bar();\n\t}\n}\n");
		write("src/test/java/FooTest.java", "class FooTest {\n\tvoid t() {\n\t\tassertEquals(1, new Foo().bar());\n\t}\n}\n");
		write("notes.txt", "nothing to see\n");
	}

	private void write(String relative, String content) throws Exception {
		Path file = temp.toPath().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	// --- read_file ---------------------------------------------------------

	@Test
	void testReadFileReturnsNumberedContent() throws Exception {
		String out = new ReadFileTool(workspace).execute("{\"path\":\"src/main/java/Foo.java\"}");

		assertTrue(out.contains("class Foo"), out);
		assertTrue(out.contains("1:"), "lines should be numbered so the model can cite them: " + out);
	}

	@Test
	void testReadFileHonoursALineRange() throws Exception {
		String out = new ReadFileTool(workspace)
				.execute("{\"path\":\"src/main/java/Foo.java\",\"startLine\":2,\"endLine\":3}");

		assertTrue(out.contains("class Foo"), out);
		assertFalse(out.contains("return 1"), out);
	}

	@Test
	void testReadFileRejectsTraversal() {
		ReadFileTool tool = new ReadFileTool(workspace);

		assertThrows(IOException.class, () -> tool.execute("{\"path\":\"../../etc/passwd\"}"));
		assertThrows(IOException.class, () -> tool.execute("{\"path\":\"src/../../outside.txt\"}"));
	}

	@Test
	void testReadFileRejectsAbsolutePaths() {
		ReadFileTool tool = new ReadFileTool(workspace);

		assertThrows(IOException.class, () -> tool.execute("{\"path\":\"/etc/passwd\"}"));
	}

	@Test
	void testReadFileReportsMissingFile() {
		ReadFileTool tool = new ReadFileTool(workspace);

		IOException thrown = assertThrows(IOException.class,
				() -> tool.execute("{\"path\":\"src/main/java/Nope.java\"}"));
		assertTrue(thrown.getMessage().contains("Nope.java"), thrown.getMessage());
	}

	@Test
	void testReadFileRequiresAPath() {
		ReadFileTool tool = new ReadFileTool(workspace);

		assertThrows(IOException.class, () -> tool.execute("{}"));
	}

	// --- grep --------------------------------------------------------------

	@Test
	void testGrepFindsMatchesWithFileAndLine() throws Exception {
		String out = new GrepTool(workspace).execute("{\"pattern\":\"class Foo\"}");

		assertTrue(out.contains("src/main/java/Foo.java"), out);
		assertTrue(out.contains(":2:"), "matches should carry a line number: " + out);
	}

	@Test
	void testGrepFindsCallersAcrossFiles() throws Exception {
		String out = new GrepTool(workspace).execute("{\"pattern\":\"\\\\.bar\\\\(\\\\)\"}");

		assertTrue(out.contains("Bar.java"), out);
		assertTrue(out.contains("FooTest.java"), out);
	}

	@Test
	void testGrepCanBeScopedByGlob() throws Exception {
		String out = new GrepTool(workspace)
				.execute("{\"pattern\":\"bar\",\"glob\":\"src/test/**\"}");

		assertTrue(out.contains("FooTest.java"), out);
		assertFalse(out.contains("src/main"), out);
	}

	@Test
	void testGrepSaysSoWhenNothingMatches() throws Exception {
		String out = new GrepTool(workspace).execute("{\"pattern\":\"zzzznotpresent\"}");

		assertTrue(out.toLowerCase(Locale.ROOT).contains("no match"), out);
	}

	@Test
	void testGrepRejectsAnInvalidPattern() {
		GrepTool tool = new GrepTool(workspace);

		assertThrows(IOException.class, () -> tool.execute("{\"pattern\":\"[unclosed\"}"));
	}

	// --- glob --------------------------------------------------------------

	@Test
	void testGlobListsMatchingPaths() throws Exception {
		String out = new GlobTool(workspace).execute("{\"glob\":\"src/main/java/*.java\"}");

		assertTrue(out.contains("src/main/java/Foo.java"), out);
		assertTrue(out.contains("src/main/java/Bar.java"), out);
		assertFalse(out.contains("notes.txt"), out);
	}

	@Test
	void testGlobOutputIsSorted() throws Exception {
		String out = new GlobTool(workspace).execute("{\"glob\":\"src/main/java/*.java\"}");

		assertTrue(out.indexOf("Bar.java") < out.indexOf("Foo.java"), out);
	}

	@Test
	void testGlobSaysSoWhenNothingMatches() throws Exception {
		String out = new GlobTool(workspace).execute("{\"glob\":\"**/*.kt\"}");

		assertTrue(out.toLowerCase(Locale.ROOT).contains("no match"), out);
	}

	// --- shared contract ---------------------------------------------------

	@Test
	void testEveryToolDeclaresAUsableSpec() {
		for (AiTool tool : List.of(new ReadFileTool(workspace), new GrepTool(workspace),
				new GlobTool(workspace))) {

			assertFalse(tool.getName().isBlank());
			assertTrue(tool.getDescription().length() > 20,
					tool.getName() + " needs a description the model can act on");
			assertTrue(tool.getInputSchema().contains("\"type\""), tool.getName());
		}
	}

	@Test
	void testToolNamesAreDistinct() {
		assertEquals(3, Set.of(
				new ReadFileTool(workspace).getName(),
				new GrepTool(workspace).getName(),
				new GlobTool(workspace).getName()).size());
	}
}
