package org.jenkinsci.plugins.p4.heal.context;

import hudson.FilePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidelineLoaderTest {

	@TempDir
	private File workspace;

	private void write(String relative, String content) throws Exception {
		Path file = workspace.toPath().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private String load() throws Exception {
		return new GuidelineLoader(GuidelineLoader.DEFAULT_GLOBS, 200_000)
				.load(new FilePath(workspace));
	}

	@Test
	void testLoadsRootClaudeMd() throws Exception {
		write("CLAUDE.md", "Indentation is tabs, not spaces.");

		String loaded = load();

		assertTrue(loaded.contains("CLAUDE.md"), loaded);
		assertTrue(loaded.contains("Indentation is tabs, not spaces."), loaded);
	}

	@Test
	void testLoadsNestedGuidelineFiles() throws Exception {
		write(".claude/guidelines/java-style.md", "No wildcard imports.");
		write(".claude/guidelines/testing.md", "JUnit 5 only.");

		String loaded = load();

		assertTrue(loaded.contains("No wildcard imports."), loaded);
		assertTrue(loaded.contains("JUnit 5 only."), loaded);
	}

	@Test
	void testIgnoresFilesThatAreNotGuidelines() throws Exception {
		write("CLAUDE.md", "keep me");
		write("README.md", "ignore me");
		write("src/main/java/Foo.java", "ignore me too");

		String loaded = load();

		assertTrue(loaded.contains("keep me"), loaded);
		assertFalse(loaded.contains("ignore me"), loaded);
	}

	@Test
	void testMissingGuidelinesYieldEmptyString() throws Exception {
		write("src/main/java/Foo.java", "class Foo {}");

		assertEquals("", load());
	}

	@Test
	void testEmptyWorkspaceYieldsEmptyString() throws Exception {
		assertEquals("", load());
	}

	@Test
	void testOrderingIsDeterministic() throws Exception {
		write("CLAUDE.md", "root");
		write(".claude/guidelines/zebra.md", "z");
		write(".claude/guidelines/alpha.md", "a");
		write("AGENTS.md", "agents");

		assertEquals(load(), load());
		assertTrue(load().indexOf("alpha.md") < load().indexOf("zebra.md"), load());
	}

	@Test
	void testEachFileIsLabelledWithItsRelativePath() throws Exception {
		write(".claude/guidelines/java-style.md", "tabs");

		String loaded = load();

		assertTrue(loaded.contains(".claude/guidelines/java-style.md"), loaded);
	}

	@Test
	void testHonoursCustomGlobs() throws Exception {
		write("docs/house-rules.md", "custom rules");
		write("CLAUDE.md", "default rules");

		String loaded = new GuidelineLoader(List.of("docs/*.md"), 200_000)
				.load(new FilePath(workspace));

		assertTrue(loaded.contains("custom rules"), loaded);
		assertFalse(loaded.contains("default rules"), loaded);
	}

	@Test
	void testStopsAtByteCapAndSaysSo() throws Exception {
		write(".claude/guidelines/aaa.md", "x".repeat(400));
		write(".claude/guidelines/zzz.md", "y".repeat(400));

		String loaded = new GuidelineLoader(GuidelineLoader.DEFAULT_GLOBS, 500)
				.load(new FilePath(workspace));

		assertTrue(loaded.contains("x".repeat(400)), "first file should survive the cap");
		assertFalse(loaded.contains("y".repeat(400)), "second file should be dropped");
		assertTrue(loaded.contains("truncated"), loaded);
	}

	@Test
	void testCapIsNotReportedWhenEverythingFits() throws Exception {
		write("CLAUDE.md", "small");

		assertFalse(load().contains("truncated"), load());
	}

	@Test
	void testDefaultGlobsCoverTheCommonAiConfigFiles() {
		assertTrue(GuidelineLoader.DEFAULT_GLOBS.contains("CLAUDE.md"));
		assertTrue(GuidelineLoader.DEFAULT_GLOBS.contains("AGENTS.md"));
		assertTrue(GuidelineLoader.DEFAULT_GLOBS.stream().anyMatch(g -> g.contains(".claude/guidelines")));
	}
}
