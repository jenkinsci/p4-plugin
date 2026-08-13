package org.jenkinsci.plugins.p4.heal.patch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchGuardTest {

	private static final List<String> SRC_ONLY = List.of("src/main/java/", "src/test/java/");

	/**
	 * Build a single-hunk diff, deriving the header counts from the body so a
	 * fixture can never drift out of sync with its own header.
	 */
	private static UnifiedDiff patch(String path, String... body) throws Exception {
		int oldCount = 0;
		int newCount = 0;
		for (String line : body) {
			char marker = line.isEmpty() ? ' ' : line.charAt(0);
			if (marker == '-') {
				oldCount++;
			} else if (marker == '+') {
				newCount++;
			} else {
				oldCount++;
				newCount++;
			}
		}

		StringBuilder text = new StringBuilder();
		text.append("--- a/").append(path).append('\n');
		text.append("+++ b/").append(path).append('\n');
		text.append("@@ -1,").append(oldCount).append(" +1,").append(newCount).append(" @@\n");
		for (String line : body) {
			text.append(line).append('\n');
		}
		return UnifiedDiff.parse(text.toString());
	}

	private static PatchGuard guard() {
		return new PatchGuard(SRC_ONLY, false);
	}

	private static void assertViolation(List<String> violations, String fragment) {
		assertFalse(violations.isEmpty(), "expected a violation mentioning '" + fragment + "'");
		assertTrue(violations.stream().anyMatch(v -> v.contains(fragment)),
				"expected a violation mentioning '" + fragment + "' but got " + violations);
	}

	@Test
	void testCleanPatchIsAllowed() throws Exception {
		List<String> violations = guard().check(patch("src/main/java/Foo.java",
				" public class Foo {",
				"-		int x = 1;",
				"+		int x = 2;",
				" }"));

		assertEquals(List.of(), violations);
	}

	@Test
	void testAddingTestToTestFileIsAllowed() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" class FooTest {",
				"+	@Test",
				"+	void testNewBehaviour() {",
				"+		assertEquals(2, Foo.bar());",
				"+	}",
				" }"));

		assertEquals(List.of(), violations);
	}

	@Test
	void testRejectsAddedDisabledAnnotation() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" class FooTest {",
				"+	@Disabled(\"flaky\")",
				" 	@Test",
				" 	void testBar() {",
				" 	}",
				" }"));

		assertViolation(violations, "@Disabled");
	}

	@Test
	void testRejectsAddedIgnoreAnnotation() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" class FooTest {",
				"+	@Ignore",
				" 	@Test",
				" 	void testBar() {",
				" 	}",
				" }"));

		assertViolation(violations, "@Ignore");
	}

	@Test
	void testRejectsAddedAssumptionShortCircuit() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" 	void testBar() {",
				"+		assumeTrue(false);",
				" 	}"));

		assertViolation(violations, "assumeTrue(false)");
	}

	@Test
	void testRejectsAddedSkipTestsFlag() throws Exception {
		List<String> violations = guard().check(patch("src/main/java/Runner.java",
				" 	void run() {",
				"+		args.add(\"-DskipTests\");",
				" 	}"));

		assertViolation(violations, "skipTests");
	}

	@Test
	void testRejectsDeletedTestMethod() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" class FooTest {",
				"-	@Test",
				"-	void testBar() {",
				"-		assertEquals(1, Foo.bar());",
				"-	}",
				" }"));

		assertViolation(violations, "@Test");
	}

	@Test
	void testRejectsNetRemovalOfAssertionsInTestFile() throws Exception {
		List<String> violations = guard().check(patch("src/test/java/FooTest.java",
				" 	void testBar() {",
				"-		assertEquals(1, Foo.bar());",
				"-		assertTrue(Foo.isReady());",
				"+		Foo.bar();",
				" 	}"));

		assertViolation(violations, "assertion");
	}

	@Test
	void testAllowsRemovingAssertionsOutsideTestFiles() throws Exception {
		List<String> violations = guard().check(patch("src/main/java/Foo.java",
				" 	void bar() {",
				"-		assert x > 0;",
				"+		if (x <= 0) {",
				"+			throw new IllegalStateException();",
				"+		}",
				" 	}"));

		assertEquals(List.of(), violations);
	}

	@Test
	void testRejectsPathOutsideAllowlist() throws Exception {
		List<String> violations = guard().check(patch("scripts/deploy.sh",
				"-echo old",
				"+echo new"));

		assertViolation(violations, "scripts/deploy.sh");
	}

	@Test
	void testRejectsBuildConfigByDefault() throws Exception {
		assertViolation(guard().check(patch("pom.xml",
				"-		<skipTests>false</skipTests>",
				"+		<skipTests>true</skipTests>")), "pom.xml");

		assertViolation(new PatchGuard(List.of(), false).check(patch("Jenkinsfile",
				"-	sh 'mvn test'",
				"+	sh 'mvn test -DskipTests'")), "Jenkinsfile");

		assertViolation(new PatchGuard(List.of(), false).check(patch("build.gradle",
				"-	test { }",
				"+	test { enabled = false }")), "build.gradle");
	}

	@Test
	void testAllowsBuildConfigWhenExplicitlyPermitted() throws Exception {
		List<String> violations = new PatchGuard(List.of(), true).check(patch("pom.xml",
				" 	<version>1.0</version>",
				"-		<foo>1</foo>",
				"+		<foo>2</foo>"));

		assertEquals(List.of(), violations);
	}

	@Test
	void testEmptyAllowlistPermitsAnyNonBuildPath() throws Exception {
		List<String> violations = new PatchGuard(List.of(), false).check(patch("scripts/deploy.sh",
				"-echo old",
				"+echo new"));

		assertEquals(List.of(), violations);
	}

	@Test
	void testReportsEveryViolationNotJustTheFirst() throws Exception {
		String text = "--- a/scripts/run.sh\n"
				+ "+++ b/scripts/run.sh\n"
				+ "@@ -1,1 +1,2 @@\n"
				+ " set -e\n"
				+ "+mvn test -DskipTests\n"
				+ "--- a/src/test/java/FooTest.java\n"
				+ "+++ b/src/test/java/FooTest.java\n"
				+ "@@ -1,2 +1,2 @@\n"
				+ " class FooTest {\n"
				+ "-	@Test void testBar() { assertTrue(true); }\n"
				+ "+	@Disabled void testBar() { }\n";

		List<String> violations = guard().check(UnifiedDiff.parse(text));

		assertViolation(violations, "scripts/run.sh");
		assertViolation(violations, "skipTests");
		assertViolation(violations, "@Disabled");
		assertViolation(violations, "@Test");
	}

	@Test
	void testTestFileDetectionCoversCommonSuffixes() throws Exception {
		assertViolation(guard().check(patch("src/test/java/FooTests.java",
				"-		assertEquals(1, x);",
				"+		int y = 1;")), "assertion");

		assertViolation(new PatchGuard(List.of(), false).check(patch("module/src/test/java/BarIT.java",
				"-		assertNotNull(x);",
				"+		Object y = x;")), "assertion");
	}
}
