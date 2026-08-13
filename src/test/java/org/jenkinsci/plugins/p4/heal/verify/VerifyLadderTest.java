package org.jenkinsci.plugins.p4.heal.verify;

import hudson.EnvVars;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyLadderTest {

	private static final String COMPILE = "mvn -B compile";
	private static final String TARGETED = "mvn -B test -Dtest={tests}";
	private static final String VERIFY = "mvn -B verify";

	private static final VerifyConfig CONFIG = new VerifyConfig(COMPILE, TARGETED, VERIFY, 2);

	/**
	 * Scripts command outcomes by the command text, and records the order commands
	 * were actually run so short-circuiting can be asserted.
	 */
	private static final class ScriptedVerifier extends BuildVerifier {

		private final Map<String, List<CommandResult>> scripted = new LinkedHashMap<>();
		private final List<String> executed = new ArrayList<>();
		private TestResults results = empty();

		ScriptedVerifier() {
			super(null, null, null, new EnvVars());
		}

		ScriptedVerifier onCommand(String command, CommandResult... outcomes) {
			scripted.put(command, new ArrayList<>(List.of(outcomes)));
			return this;
		}

		ScriptedVerifier producing(TestResults results) {
			this.results = results;
			return this;
		}

		@Override
		public CommandResult run(String command) {
			executed.add(command);
			List<CommandResult> queued = scripted.get(command);
			if (queued == null || queued.isEmpty()) {
				return new CommandResult(0, "ok");
			}
			return queued.size() == 1 ? queued.get(0) : queued.remove(0);
		}

		@Override
		public TestResults readTestResults() {
			return results;
		}
	}

	private static TestResults empty() {
		try {
			return TestResults.parseAll(List.of());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static TestResults results(String xml) {
		try {
			return TestResults.parse(xml);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String suite(String body) {
		return "<?xml version=\"1.0\"?><testsuite name=\"com.example.FooTest\">" + body
				+ "</testsuite>";
	}

	private static String passing(String method) {
		return "<testcase name=\"" + method + "\" classname=\"com.example.FooTest\"/>";
	}

	private static String failing(String method) {
		return "<testcase name=\"" + method + "\" classname=\"com.example.FooTest\">"
				+ "<failure message=\"nope\">trace</failure></testcase>";
	}

	private static final TestResults BASELINE =
			results(suite(failing("testBroken") + passing("testHealthy")));

	private static final Set<String> MUST_PASS = Set.of("com.example.FooTest#testBroken");

	@Test
	void testPassesWhenEveryRungIsGreen() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertTrue(result.passed(), result.detail());
	}

	@Test
	void testCompileFailureStopsTheLadderImmediately() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.onCommand(COMPILE, new CommandResult(1, "cannot find symbol"));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertEquals(Rung.COMPILE, result.failedAt());
		assertTrue(result.detail().contains("cannot find symbol"), result.detail());
		assertEquals(List.of(COMPILE), verifier.executed, "nothing should run after compile fails");
	}

	@Test
	void testTargetedRunSubstitutesTheFailingTestIds() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));

		new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertTrue(verifier.executed.contains("mvn -B test -Dtest=com.example.FooTest#testBroken"),
				verifier.executed.toString());
	}

	@Test
	void testFailsWhenTheOriginallyBrokenTestStillFails() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(failing("testBroken") + passing("testHealthy"))));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertEquals(Rung.TARGETED_TESTS, result.failedAt());
		assertTrue(result.detail().contains("testBroken"), result.detail());
	}

	@Test
	void testFailsWhenTheFullVerifyCommandFails() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))))
				.onCommand(VERIFY, new CommandResult(1, "integration test exploded"));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertEquals(Rung.FULL_VERIFY, result.failedAt());
		assertTrue(result.detail().contains("integration test exploded"), result.detail());
	}

	@Test
	void testFailsWhenThePatchBreaksAPreviouslyPassingTest() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + failing("testHealthy"))));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertEquals(Rung.REGRESSION, result.failedAt());
		assertTrue(result.detail().contains("testHealthy"),
				"the regression must be named so the next attempt can act on it: " + result.detail());
	}

	@Test
	void testFailsWhenAFlakeRerunGoesRed() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))))
				.onCommand(VERIFY,
						new CommandResult(0, "green"),
						new CommandResult(1, "intermittent failure"),
						new CommandResult(0, "green"));

		LadderResult result = new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertEquals(Rung.FLAKE, result.failedAt());
		assertTrue(result.detail().contains("intermittent failure"), result.detail());
	}

	@Test
	void testRepeatsTheVerifyCommandOncePerConfiguredRerun() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));

		new VerifyLadder(verifier, CONFIG).run(BASELINE, MUST_PASS);

		long verifyRuns = verifier.executed.stream().filter(VERIFY::equals).count();
		assertEquals(3, verifyRuns, "one real run plus two flake re-runs");
	}

	@Test
	void testNoRerunsWhenFlakeCheckingIsOff() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));
		VerifyConfig noReruns = new VerifyConfig(COMPILE, TARGETED, VERIFY, 0);

		LadderResult result = new VerifyLadder(verifier, noReruns).run(BASELINE, MUST_PASS);

		assertTrue(result.passed());
		assertEquals(1, verifier.executed.stream().filter(VERIFY::equals).count());
	}

	@Test
	void testTargetedRungIsSkippedWhenNoCommandIsConfigured() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));
		VerifyConfig noTargeted = new VerifyConfig(COMPILE, "", VERIFY, 0);

		LadderResult result = new VerifyLadder(verifier, noTargeted).run(BASELINE, MUST_PASS);

		assertTrue(result.passed());
		assertFalse(verifier.executed.stream().anyMatch(c -> c.startsWith("mvn -B test")));
	}

	@Test
	void testRungsRunInLadderOrder() throws Exception {
		ScriptedVerifier verifier = new ScriptedVerifier()
				.producing(results(suite(passing("testBroken") + passing("testHealthy"))));

		new VerifyLadder(verifier, new VerifyConfig(COMPILE, TARGETED, VERIFY, 0))
				.run(BASELINE, MUST_PASS);

		assertEquals(COMPILE, verifier.executed.get(0));
		assertTrue(verifier.executed.get(1).startsWith("mvn -B test"));
		assertEquals(VERIFY, verifier.executed.get(2));
	}

	@Test
	void testCommandFailurePropagatesAsALadderFailureNotAnException() throws Exception {
		BuildVerifier exploding = new BuildVerifier(null, null, null, new EnvVars()) {
			@Override
			public CommandResult run(String command) throws IOException {
				throw new IOException("agent went away");
			}
		};

		LadderResult result = new VerifyLadder(exploding, CONFIG).run(BASELINE, MUST_PASS);

		assertFalse(result.passed());
		assertTrue(result.detail().contains("agent went away"), result.detail());
	}
}
