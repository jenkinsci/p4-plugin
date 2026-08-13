package org.jenkinsci.plugins.p4.heal.verify;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResultsTest {

	private static final String SUITE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<testsuite name="com.example.FooTest" tests="4">
				<testcase name="testPasses" classname="com.example.FooTest" time="0.01"/>
				<testcase name="testFails" classname="com.example.FooTest" time="0.02">
					<failure message="expected 1 but was 2" type="java.lang.AssertionError">at Foo.bar</failure>
				</testcase>
				<testcase name="testErrors" classname="com.example.FooTest">
					<error message="boom" type="java.lang.IllegalStateException">at Foo.baz</error>
				</testcase>
				<testcase name="testSkipped" classname="com.example.FooTest">
					<skipped/>
				</testcase>
			</testsuite>
			""";

	private static String suite(String className, String body) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<testsuite name=\"" + className + "\">\n" + body + "\n</testsuite>\n";
	}

	private static String passing(String className, String method) {
		return suite(className, "<testcase name=\"" + method
				+ "\" classname=\"" + className + "\"/>");
	}

	private static String failing(String className, String method) {
		return suite(className, "<testcase name=\"" + method + "\" classname=\"" + className + "\">"
				+ "<failure message=\"nope\" type=\"java.lang.AssertionError\">trace</failure>"
				+ "</testcase>");
	}

	@Test
	void testParseClassifiesEveryOutcome() throws Exception {
		TestResults results = TestResults.parse(SUITE);

		assertEquals(Set.of("com.example.FooTest#testFails", "com.example.FooTest#testErrors"),
				results.getFailed());
		assertEquals(Set.of("com.example.FooTest#testPasses"), results.getPassed());
		assertEquals(Set.of("com.example.FooTest#testSkipped"), results.getSkipped());
	}

	@Test
	void testParseKeepsFailureMessageForFeedback() throws Exception {
		TestResults results = TestResults.parse(SUITE);

		assertTrue(results.getFailureDetail("com.example.FooTest#testFails")
				.contains("expected 1 but was 2"));
		assertTrue(results.getFailureDetail("com.example.FooTest#testErrors").contains("boom"));
	}

	@Test
	void testFailureDetailOfUnknownTestIsEmpty() throws Exception {
		assertEquals("", TestResults.parse(SUITE).getFailureDetail("com.example.FooTest#nope"));
	}

	@Test
	void testParseEmptySuite() throws Exception {
		TestResults results = TestResults.parse(suite("com.example.EmptyTest", ""));

		assertEquals(Set.of(), results.getFailed());
		assertEquals(Set.of(), results.getPassed());
	}

	@Test
	void testParseAllMergesSuites() throws Exception {
		TestResults results = TestResults.parseAll(List.of(
				passing("com.example.OneTest", "testA"),
				failing("com.example.TwoTest", "testB")));

		assertEquals(Set.of("com.example.OneTest#testA"), results.getPassed());
		assertEquals(Set.of("com.example.TwoTest#testB"), results.getFailed());
	}

	@Test
	void testParseAllOfNothingIsEmpty() throws Exception {
		TestResults results = TestResults.parseAll(List.of());

		assertEquals(Set.of(), results.getFailed());
		assertEquals(Set.of(), results.getPassed());
	}

	@Test
	void testNewlyFailingDetectsRegression() throws Exception {
		TestResults before = TestResults.parseAll(List.of(
				passing("com.example.OneTest", "testA"),
				failing("com.example.TwoTest", "testB")));
		TestResults after = TestResults.parseAll(List.of(
				failing("com.example.OneTest", "testA"),
				passing("com.example.TwoTest", "testB")));

		assertEquals(Set.of("com.example.OneTest#testA"), after.newlyFailingSince(before));
	}

	@Test
	void testNewlyFailingIgnoresPreExistingFailure() throws Exception {
		TestResults before = TestResults.parseAll(List.of(failing("com.example.OneTest", "testA")));
		TestResults after = TestResults.parseAll(List.of(failing("com.example.OneTest", "testA")));

		assertEquals(Set.of(), after.newlyFailingSince(before));
	}

	@Test
	void testNewlyFailingCountsTestAbsentFromBaseline() throws Exception {
		TestResults before = TestResults.parseAll(List.of(passing("com.example.OneTest", "testA")));
		TestResults after = TestResults.parseAll(List.of(
				passing("com.example.OneTest", "testA"),
				failing("com.example.NewTest", "testB")));

		assertEquals(Set.of("com.example.NewTest#testB"), after.newlyFailingSince(before));
	}

	@Test
	void testStillFailingReportsUnfixedTests() throws Exception {
		TestResults before = TestResults.parseAll(List.of(
				failing("com.example.OneTest", "testA"),
				failing("com.example.TwoTest", "testB")));
		TestResults after = TestResults.parseAll(List.of(
				failing("com.example.OneTest", "testA"),
				passing("com.example.TwoTest", "testB")));

		assertEquals(Set.of("com.example.OneTest#testA"), after.stillFailingSince(before));
	}

	@Test
	void testDisappearedTestCountsAsStillFailing() throws Exception {
		TestResults before = TestResults.parseAll(List.of(failing("com.example.OneTest", "testA")));
		TestResults after = TestResults.parseAll(List.of(passing("com.example.OtherTest", "testZ")));

		assertEquals(Set.of("com.example.OneTest#testA"), after.stillFailingSince(before));
	}

	@Test
	void testRejectsMalformedXml() {
		assertThrows(IOException.class, () -> TestResults.parse("<testsuite><oops"));
	}

	@Test
	void testDoesNotResolveExternalEntities() {
		String xxe = "<?xml version=\"1.0\"?>\n"
				+ "<!DOCTYPE t [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>\n"
				+ "<testsuite name=\"com.example.XTest\">"
				+ "<testcase name=\"&x;\" classname=\"com.example.XTest\"/>"
				+ "</testsuite>";

		assertThrows(IOException.class, () -> TestResults.parse(xxe));
	}
}
