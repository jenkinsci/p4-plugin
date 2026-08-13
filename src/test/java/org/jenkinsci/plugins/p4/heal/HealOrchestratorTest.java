package org.jenkinsci.plugins.p4.heal;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.p4.heal.agent.HealModels;
import org.jenkinsci.plugins.p4.heal.context.FailureContext;
import org.jenkinsci.plugins.p4.heal.patch.PatchGuard;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.jenkinsci.plugins.p4.heal.provider.FakeAiProvider;
import org.jenkinsci.plugins.p4.heal.verify.LadderResult;
import org.jenkinsci.plugins.p4.heal.verify.Rung;
import org.jenkinsci.plugins.p4.heal.verify.TestResults;
import org.jenkinsci.plugins.p4.heal.verify.VerifyLadder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealOrchestratorTest {

	private static final HealModels MODELS =
			new HealModels("smart", "cheap", "xhigh", "medium", 16000);

	private static final String ACCEPT = "VERDICT: ACCEPT\nlooks right";
	private static final String REJECT = "VERDICT: REJECT\nmasks the symptom";

	private static String diff(String path, String from, String to) {
		return "--- a/" + path + "\n+++ b/" + path + "\n@@ -1,1 +1,1 @@\n-" + from + "\n+" + to + "\n";
	}

	private static final String DIFF_ONE = diff("src/main/java/Foo.java", "int x = 1;", "int x = 2;");
	private static final String DIFF_TWO = diff("src/main/java/Foo.java", "int x = 1;", "int x = 3;");
	private static final String DIFF_THREE = diff("src/main/java/Foo.java", "int x = 1;", "int x = 4;");

	private final ByteArrayOutputStream console = new ByteArrayOutputStream();
	private final TaskListener listener = new StreamTaskListener(console, StandardCharsets.UTF_8);

	private String consoleText() {
		return console.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Records what the workspace was asked to do, so the loop's discipline about
	 * reverting rejected candidates can be asserted.
	 */
	private static final class RecordingSession implements PatchSession {

		private final List<String> events = new ArrayList<>();

		@Override
		public void apply(UnifiedDiff patch) {
			events.add("apply");
		}

		@Override
		public void revert() {
			events.add("revert");
		}

		@Override
		public String deliver(UnifiedDiff patch, String description) {
			events.add("deliver");
			return "shelved-12345";
		}
	}

	private static final class ScriptedLadder extends VerifyLadder {

		private final Deque<LadderResult> results = new ArrayDeque<>();

		ScriptedLadder(LadderResult... scripted) {
			super(null, null);
			for (LadderResult result : scripted) {
				results.add(result);
			}
		}

		@Override
		public LadderResult run(TestResults baseline, Set<String> mustPass) {
			return results.isEmpty() ? LadderResult.pass() : results.removeFirst();
		}
	}

	private static FailureContext context() {
		return new FailureContext("[ERROR] boom",
				Map.of("com.example.FooTest#testBar", "expected 1 but was 2"),
				List.of("src/main/java/Foo.java"));
	}

	private static TestResults baseline() throws IOException {
		return TestResults.parse("<?xml version=\"1.0\"?><testsuite name=\"com.example.FooTest\">"
				+ "<testcase name=\"testBar\" classname=\"com.example.FooTest\">"
				+ "<failure message=\"nope\">trace</failure></testcase></testsuite>");
	}

	private HealOrchestrator orchestrator(FakeAiProvider provider, VerifyLadder ladder,
	                                      PatchSession session, HealLimits limits) {
		return new HealOrchestrator(provider, MODELS, List.of(),
				new PatchGuard(List.of("src/"), false), ladder, session, limits, listener);
	}

	/**
	 * One full attempt: two diagnoses, three candidates, then four critics for each
	 * surviving candidate in rank order.
	 */
	private static FakeAiProvider providerFor(String... afterDiagnosis) {
		FakeAiProvider provider = new FakeAiProvider()
				.willSay("compile diagnosis", "test diagnosis");
		provider.willSay(afterDiagnosis);
		return provider;
	}

	private static String[] threeCandidatesThenCritics(String... criticAnswers) {
		List<String> script = new ArrayList<>(List.of(DIFF_ONE, DIFF_TWO, DIFF_THREE));
		script.addAll(List.of(criticAnswers));
		return script.toArray(new String[0]);
	}

	private static String[] allCriticsAccept(int candidates) {
		List<String> answers = new ArrayList<>();
		for (int i = 0; i < candidates * 4; i++) {
			answers.add(ACCEPT);
		}
		return answers.toArray(new String[0]);
	}

	@Test
	void testDeliversAPatchThatClearsEveryGate() throws Exception {
		RecordingSession session = new RecordingSession();
		FakeAiProvider provider = providerFor(
				threeCandidatesThenCritics(allCriticsAccept(3)));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(LadderResult.pass()),
				session, HealLimits.defaults()).run("guidelines", context(), baseline());

		assertTrue(outcome.healed(), outcome.detail());
		assertEquals("shelved-12345", outcome.delivered());
		assertTrue(session.events.contains("deliver"));
	}

	@Test
	void testRevertsAfterAFailedGateAndFallsBackToTheNextCandidate() throws Exception {
		RecordingSession session = new RecordingSession();
		FakeAiProvider provider = providerFor(
				threeCandidatesThenCritics(allCriticsAccept(3)));

		HealOutcome outcome = orchestrator(provider,
				new ScriptedLadder(LadderResult.fail(Rung.COMPILE, "cannot find symbol"),
						LadderResult.pass()),
				session, HealLimits.defaults()).run("guidelines", context(), baseline());

		assertTrue(outcome.healed(), outcome.detail());
		assertEquals(List.of("apply", "revert", "apply", "deliver"), session.events,
				"a rejected candidate must be reverted before the next is applied");
	}

	@Test
	void testMajorityRejectionDropsACandidate() throws Exception {
		RecordingSession session = new RecordingSession();
		// First candidate: three rejects. Second and third: all accept.
		FakeAiProvider provider = providerFor(threeCandidatesThenCritics(
				REJECT, REJECT, REJECT, ACCEPT,
				ACCEPT, ACCEPT, ACCEPT, ACCEPT,
				ACCEPT, ACCEPT, ACCEPT, ACCEPT));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(LadderResult.pass()),
				session, HealLimits.defaults()).run("guidelines", context(), baseline());

		assertTrue(outcome.healed(), outcome.detail());
		assertEquals(1, session.events.stream().filter("apply"::equals).count(),
				"the rejected candidate should never reach the workspace");
	}

	@Test
	void testCandidateFailingTheGuardNeverReachesTheCritics() throws Exception {
		RecordingSession session = new RecordingSession();
		String disabling = "--- a/src/test/java/FooTest.java\n+++ b/src/test/java/FooTest.java\n"
				+ "@@ -1,1 +1,2 @@\n class FooTest {\n+	@Disabled\n";

		FakeAiProvider provider = new FakeAiProvider()
				.willSay("compile diagnosis", "test diagnosis")
				.willSay(disabling, DIFF_TWO, DIFF_THREE)
				.willSay(allCriticsAccept(2));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(LadderResult.pass()),
				session, HealLimits.defaults()).run("guidelines", context(), baseline());

		assertTrue(outcome.healed(), outcome.detail());
		assertTrue(consoleText().contains("@Disabled"), consoleText());
	}

	@Test
	void testGivesUpWhenNoCandidateSurvivesTheCritics() throws Exception {
		RecordingSession session = new RecordingSession();
		List<String> script = new ArrayList<>();
		for (int attempt = 0; attempt < 2; attempt++) {
			script.add("compile diagnosis");
			script.add("test diagnosis");
			script.addAll(List.of(DIFF_ONE, DIFF_TWO, DIFF_THREE));
			for (int i = 0; i < 12; i++) {
				script.add(REJECT);
			}
		}
		FakeAiProvider provider = new FakeAiProvider().willSay(script.toArray(new String[0]));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(),
				session, new HealLimits(2, 2, 0)).run("guidelines", context(), baseline());

		assertFalse(outcome.healed());
		assertEquals(HealOutcome.Status.ALL_REJECTED, outcome.status());
		assertFalse(session.events.contains("apply"));
	}

	@Test
	void testGivesUpWhenNothingParsesAsAPatch() throws Exception {
		FakeAiProvider provider = new FakeAiProvider()
				.willSay("compile diagnosis", "test diagnosis")
				.willSay("NONE", "NONE", "I could not work out a fix.");

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(), new RecordingSession(),
				new HealLimits(1, 2, 0)).run("guidelines", context(), baseline());

		assertEquals(HealOutcome.Status.NO_CANDIDATE, outcome.status(), outcome.detail());
	}

	@Test
	void testFeedsVerificationFailureBackIntoTheNextAttempt() throws Exception {
		List<String> script = new ArrayList<>();
		for (int attempt = 0; attempt < 2; attempt++) {
			script.add("compile diagnosis");
			script.add("test diagnosis");
			script.addAll(List.of(DIFF_ONE, DIFF_TWO, DIFF_THREE));
			script.addAll(List.of(allCriticsAccept(3)));
		}
		FakeAiProvider provider = new FakeAiProvider().willSay(script.toArray(new String[0]));

		orchestrator(provider,
				new ScriptedLadder(
						LadderResult.fail(Rung.COMPILE, "cannot find symbol: parseInt"),
						LadderResult.fail(Rung.COMPILE, "cannot find symbol: parseInt"),
						LadderResult.fail(Rung.COMPILE, "cannot find symbol: parseInt"),
						LadderResult.pass()),
				new RecordingSession(), new HealLimits(2, 2, 0))
				.run("guidelines", context(), baseline());

		boolean feedbackReached = provider.getRequests().stream()
				.skip(15)
				.anyMatch(r -> r.user().contains("cannot find symbol: parseInt"));
		assertTrue(feedbackReached,
				"the second attempt must see the real compiler output from the first");
	}

	@Test
	void testReportsVerificationFailureWhenAttemptsRunOut() throws Exception {
		List<String> script = new ArrayList<>();
		script.add("compile diagnosis");
		script.add("test diagnosis");
		script.addAll(List.of(DIFF_ONE, DIFF_TWO, DIFF_THREE));
		script.addAll(List.of(allCriticsAccept(3)));
		FakeAiProvider provider = new FakeAiProvider().willSay(script.toArray(new String[0]));

		HealOutcome outcome = orchestrator(provider,
				new ScriptedLadder(
						LadderResult.fail(Rung.FULL_VERIFY, "tests failed"),
						LadderResult.fail(Rung.FULL_VERIFY, "tests failed"),
						LadderResult.fail(Rung.FULL_VERIFY, "tests failed")),
				new RecordingSession(), new HealLimits(1, 2, 0))
				.run("guidelines", context(), baseline());

		assertEquals(HealOutcome.Status.VERIFICATION_FAILED, outcome.status());
		assertTrue(outcome.detail().contains("tests failed"), outcome.detail());
	}

	@Test
	void testStopsWhenTheTokenBudgetIsSpent() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("compile diagnosis", "test diagnosis");

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(), new RecordingSession(),
				new HealLimits(3, 2, 200)).run("guidelines", context(), baseline());

		assertEquals(HealOutcome.Status.BUDGET_EXHAUSTED, outcome.status(), outcome.detail());
	}

	@Test
	void testProviderFailureIsReportedNotThrown() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willFail(new IOException("connection reset"));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(), new RecordingSession(),
				HealLimits.defaults()).run("guidelines", context(), baseline());

		assertEquals(HealOutcome.Status.ERROR, outcome.status());
		assertTrue(outcome.detail().contains("connection reset"), outcome.detail());
	}

	@Test
	void testConsoleNarratesTheLadderSoAHumanCanAudit() throws Exception {
		FakeAiProvider provider = providerFor(threeCandidatesThenCritics(allCriticsAccept(3)));

		orchestrator(provider, new ScriptedLadder(LadderResult.pass()), new RecordingSession(),
				HealLimits.defaults()).run("guidelines", context(), baseline());

		String log = consoleText();
		assertTrue(log.contains("attempt 1"), log);
		assertTrue(log.contains("candidate"), log);
		assertTrue(log.contains("VERIFIED") || log.contains("verified"), log);
	}

	@Test
	void testTokensAreAccountedAcrossTheWholeRun() throws Exception {
		FakeAiProvider provider = providerFor(threeCandidatesThenCritics(allCriticsAccept(3)));

		HealOutcome outcome = orchestrator(provider, new ScriptedLadder(LadderResult.pass()),
				new RecordingSession(), HealLimits.defaults())
				.run("guidelines", context(), baseline());

		assertTrue(outcome.inputTokens() > 0);
		assertTrue(outcome.outputTokens() > 0);
	}
}
