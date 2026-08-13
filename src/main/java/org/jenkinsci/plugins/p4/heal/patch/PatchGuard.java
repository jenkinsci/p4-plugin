package org.jenkinsci.plugins.p4.heal.patch;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Policy gate for a generated patch, covering rungs 1 and 3 of the verify ladder.
 *
 * <p>Rung 1 confines the patch to paths the build is allowed to change. Rung 3 is
 * the anti-cheat scan: the cheapest way to make a failing build pass is to stop
 * running the test that fails, so a patch that suppresses, skips or deletes tests
 * is rejected before anything is compiled.
 *
 * <p>The bias is deliberately toward false rejection. Wrongly rejecting a sound
 * patch costs one more loop iteration; wrongly accepting one that disables a test
 * ships a green build that proves nothing.
 */
public final class PatchGuard implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");
	private static final Pattern ASSERTION = Pattern.compile("\\bassert[A-Z]\\w*\\s*\\(");

	private static final Pattern TEST_FILE_NAME =
			Pattern.compile(".*(Test|Tests|IT|ITCase)\\.java$");

	/**
	 * Ways of stopping a test from running, and what to tell the model about each.
	 * A table rather than a block per pattern, so a new way to cheat is one entry.
	 *
	 * <p>A list, not a map: these messages are fed back to the model, and the order
	 * they are reported in has to be the same on every run.
	 */
	private static final List<Suppression> SUPPRESSIONS = List.of(
			new Suppression(Pattern.compile("@Disabled\\b"),
					"adds @Disabled, which suppresses a test instead of fixing it."),
			new Suppression(Pattern.compile("@Ignore\\b"),
					"adds @Ignore, which suppresses a test instead of fixing it."),
			new Suppression(Pattern.compile("assumeTrue\\s*\\(\\s*false\\s*\\)"
					+ "|assumeFalse\\s*\\(\\s*true\\s*\\)"
					+ "|Assumptions\\s*\\.\\s*abort"),
					"adds an assumption that can never hold (assumeTrue(false) or"
							+ " equivalent), which skips the test."),
			new Suppression(Pattern.compile("skipTests|skipITs|maven\\.test\\.skip"),
					"adds a skipTests flag, which stops tests running at all."));

	/**
	 * @param pattern what to look for in the patch's added lines
	 * @param problem what to tell the model it did, after the file name
	 */
	private record Suppression(Pattern pattern, String problem) {
	}

	private final List<String> allowedPathPrefixes;
	private final boolean allowBuildConfigChanges;

	/**
	 * @param allowedPathPrefixes     workspace-relative prefixes the patch may touch;
	 *                                an empty list allows any path that is not build
	 *                                configuration
	 * @param allowBuildConfigChanges permit edits to build files such as
	 *                                {@code pom.xml} or {@code Jenkinsfile}
	 */
	public PatchGuard(List<String> allowedPathPrefixes, boolean allowBuildConfigChanges) {
		this.allowedPathPrefixes = List.copyOf(allowedPathPrefixes);
		this.allowBuildConfigChanges = allowBuildConfigChanges;
	}

	/**
	 * Check a patch against every rule.
	 *
	 * @param patch the parsed patch
	 * @return every violation found, in file order; empty when the patch is allowed.
	 *         The messages are fed back to the model verbatim on the next attempt,
	 *         so they state what was wrong rather than just that something was.
	 */
	public List<String> check(UnifiedDiff patch) {
		List<String> violations = new ArrayList<>();
		for (FileDiff file : patch.getFiles()) {
			// Both scans want these, and each call walks every hunk to build the list.
			List<String> added = file.getAddedLines();
			List<String> removed = file.getRemovedLines();

			checkPath(file, violations);
			checkTestSuppression(file.getPath(), added, violations);
			checkTestRemoval(file.getPath(), added, removed, violations);
		}
		return violations;
	}

	private void checkPath(FileDiff file, List<String> violations) {
		String path = file.getPath();

		if (!allowedPathPrefixes.isEmpty()
				&& allowedPathPrefixes.stream().noneMatch(path::startsWith)) {
			violations.add("'" + path + "' is outside the allowed paths "
					+ allowedPathPrefixes + ".");
		}
		if (!allowBuildConfigChanges && isBuildConfig(path)) {
			violations.add("'" + path + "' is build configuration; a fix may not change"
					+ " how the build itself runs.");
		}
	}

	private static void checkTestSuppression(String path, List<String> added,
	                                         List<String> violations) {

		for (Suppression suppression : SUPPRESSIONS) {
			if (count(added, suppression.pattern()) > 0) {
				violations.add("'" + path + "' " + suppression.problem());
			}
		}
	}

	private static void checkTestRemoval(String path, List<String> added, List<String> removed,
	                                     List<String> violations) {

		int testsLost = count(removed, TEST_ANNOTATION) - count(added, TEST_ANNOTATION);
		if (testsLost > 0) {
			violations.add("'" + path + "' deletes " + testsLost + " @Test method(s);"
					+ " a fix may not remove the tests that prove it.");
		}

		if (isTestFile(path)) {
			int assertionsLost = count(removed, ASSERTION) - count(added, ASSERTION);
			if (assertionsLost > 0) {
				violations.add("'" + path + "' removes " + assertionsLost
						+ " assertion(s) without replacing them, weakening the test.");
			}
		}
	}

	private static boolean isBuildConfig(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);

		if (path.startsWith(".mvn/") || path.contains("/.mvn/")
				|| path.startsWith(".github/") || path.contains("/.github/")) {
			return true;
		}
		return "pom.xml".equals(name)
				|| "package.json".equals(name)
				|| "Makefile".equals(name)
				|| "build.xml".equals(name)
				|| name.startsWith("Jenkinsfile")
				|| name.startsWith("build.gradle")
				|| name.startsWith("settings.gradle");
	}

	private static boolean isTestFile(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return path.contains("/test/") || TEST_FILE_NAME.matcher(name).matches();
	}

	private static int count(List<String> lines, Pattern pattern) {
		int found = 0;
		for (String line : lines) {
			Matcher matcher = pattern.matcher(line);
			while (matcher.find()) {
				found++;
			}
		}
		return found;
	}
}
