package org.jenkinsci.plugins.p4.heal;

import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.heal.agent.Agent;
import org.jenkinsci.plugins.p4.heal.agent.AgentRole;
import org.jenkinsci.plugins.p4.heal.agent.HealModels;
import org.jenkinsci.plugins.p4.heal.agent.Verdict;
import org.jenkinsci.plugins.p4.heal.context.FailureContext;
import org.jenkinsci.plugins.p4.heal.patch.PatchFormatException;
import org.jenkinsci.plugins.p4.heal.patch.PatchGuard;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.jenkinsci.plugins.p4.heal.provider.AiResult;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;
import org.jenkinsci.plugins.p4.heal.verify.LadderResult;
import org.jenkinsci.plugins.p4.heal.verify.TestResults;
import org.jenkinsci.plugins.p4.heal.verify.VerifyLadder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The healing loop: diagnose, generate candidates, have them critiqued, and put
 * the best through the verify ladder — repeating with real compiler and test
 * output as feedback until something passes or the run gives up.
 *
 * <p>Two ideas hold this together. First, cheap judgement runs before expensive
 * judgement: four critics reading a diff cost seconds, a full build and test run
 * costs minutes, so candidates that are obviously wrong are dropped before the
 * build is touched. Second, the critics only ever <em>narrow</em> the field —
 * nothing they approve is trusted. The ladder is the only thing that can accept
 * a patch.
 */
public final class HealOrchestrator {

	private static final List<AgentRole> DIAGNOSERS = AgentRole.diagnosers();

	private static final List<AgentRole> FIXERS = AgentRole.fixers();

	private static final List<AgentRole> CRITICS = AgentRole.critics();

	private final AiProvider provider;
	private final HealModels models;
	private final List<AiTool> tools;
	private final PatchGuard guard;
	private final VerifyLadder ladder;
	private final PatchSession session;
	private final HealLimits limits;
	private final TaskListener listener;

	private long inputTokens;
	private long outputTokens;

	/**
	 * How the run would end if no later attempt does better. Held across attempts so
	 * the console reports the last real reason a candidate was turned down rather
	 * than a bare "gave up".
	 */
	private HealOutcome.Status status = HealOutcome.Status.NO_CANDIDATE;
	private String detail = "No candidate patch was produced.";

	public HealOrchestrator(AiProvider provider, HealModels models, List<AiTool> tools,
	                        PatchGuard guard, VerifyLadder ladder, PatchSession session,
	                        HealLimits limits, TaskListener listener) {
		this.provider = provider;
		this.models = models;
		this.tools = List.copyOf(tools);
		this.guard = guard;
		this.ladder = ladder;
		this.session = session;
		this.limits = limits;
		this.listener = listener;
	}

	/**
	 * Try to heal a failed build.
	 *
	 * @param guidelines the repository's own conventions, from its AI config files
	 * @param context    what failed and why
	 * @param baseline   test results from before any patch, used to detect
	 *                   regressions
	 * @return how the run ended; never throws for an ordinary failure to fix
	 * @throws InterruptedException if the build was cancelled
	 */
	public HealOutcome run(String guidelines, FailureContext context, TestResults baseline)
			throws InterruptedException {

		String system = guidelines + "\n\n" + context.toPromptBlock();
		StringBuilder feedback = new StringBuilder();

		try {
			for (int attempt = 1; attempt <= limits.maxAttempts(); attempt++) {
				log("attempt " + attempt + " of " + limits.maxAttempts());

				HealOutcome finished = attempt(system, feedback, baseline, context);
				if (finished != null) {
					return finished;
				}
			}
		} catch (IOException e) {
			return outcome(HealOutcome.Status.ERROR, "", e.getMessage());
		}

		return outcome(status, "", detail);
	}

	/**
	 * One diagnose-generate-critique-verify round.
	 *
	 * @return the outcome the whole run should end on, or null to try again; either
	 *         way {@link #status} and {@link #detail} record why
	 */
	private HealOutcome attempt(String system, StringBuilder feedback, TestResults baseline,
	                            FailureContext context) throws IOException, InterruptedException {

		String diagnosis = diagnose(system, feedback.toString());
		if (overBudget()) {
			return budgetExhausted();
		}

		List<Candidate> candidates = generate(system, diagnosis, feedback.toString());
		if (candidates.isEmpty()) {
			return give(HealOutcome.Status.NO_CANDIDATE, "No candidate parsed as a usable patch.");
		}
		if (overBudget()) {
			return budgetExhausted();
		}

		List<Candidate> survivors = critique(system, diagnosis, candidates);
		if (survivors.isEmpty()) {
			candidates.forEach(c -> feedback.append(c.objections()).append('\n'));
			return give(HealOutcome.Status.ALL_REJECTED,
					"Every candidate was rejected by the critics.");
		}

		for (Candidate candidate : survivors) {
			LadderResult verdict = verify(candidate, baseline, context);
			if (verdict.passed()) {
				try {
					String reference = session.deliver(candidate.patch,
							"AI fix: " + firstLine(diagnosis));
					log("VERIFIED — delivered as " + reference);
					return outcome(HealOutcome.Status.HEALED, reference,
							"A patch cleared every gate.");
				} catch (DeliveryRejectedException e) {
					// The patch is at fault rather than the machinery, so the next
					// candidate still deserves its turn.
					log(e.getMessage());
					session.revert();
					give(HealOutcome.Status.VERIFICATION_FAILED, e.getMessage());
					feedback.append('\n').append(e.getMessage()).append('\n');
					continue;
				}
			}
			give(HealOutcome.Status.VERIFICATION_FAILED, verdict.detail());
			feedback.append("\nA previous attempt failed at ").append(verdict.failedAt())
					.append(":\n").append(verdict.detail()).append('\n');
		}
		return null;
	}

	/**
	 * Record why this attempt got no further, and ask for another.
	 *
	 * @return null, so callers can {@code return give(...)} to move on
	 */
	private HealOutcome give(HealOutcome.Status why, String because) {
		this.status = why;
		this.detail = because;
		return null;
	}

	private HealOutcome budgetExhausted() {
		return outcome(HealOutcome.Status.BUDGET_EXHAUSTED, "",
				"Token budget spent before a patch could be verified.");
	}

	private String diagnose(String system, String feedback) throws IOException {
		StringBuilder diagnosis = new StringBuilder();
		for (AgentRole role : DIAGNOSERS) {
			if (overBudget()) {
				return diagnosis.toString();
			}
			String answer = ask(role, system, feedback);
			diagnosis.append("## ").append(role).append('\n').append(answer).append("\n\n");
		}
		return diagnosis.toString();
	}

	private List<Candidate> generate(String system, String diagnosis, String feedback)
			throws IOException {

		List<Candidate> candidates = new ArrayList<>();
		for (AgentRole role : FIXERS) {
			if (overBudget()) {
				break;
			}
			String diff = stripFences(ask(role, system, diagnosis + "\n" + feedback));

			UnifiedDiff patch;
			try {
				patch = UnifiedDiff.parse(diff);
			} catch (PatchFormatException e) {
				log("candidate from " + role + " discarded: " + e.getMessage());
				continue;
			}

			List<String> violations = guard.check(patch);
			if (!violations.isEmpty()) {
				// Logged in full: a patch that tried to disable a test is the single
				// most important thing for a human reviewing this build to see.
				log("candidate from " + role + " rejected by the guard:");
				violations.forEach(v -> log("    " + v));
				continue;
			}
			log("candidate from " + role + " accepted for review");
			candidates.add(new Candidate(role, patch, diff));
		}
		return candidates;
	}

	private List<Candidate> critique(String system, String diagnosis, List<Candidate> candidates)
			throws IOException {

		for (Candidate candidate : candidates) {
			for (AgentRole role : CRITICS) {
				if (overBudget()) {
					break;
				}
				Verdict verdict = Verdict.parse(ask(role, system,
						diagnosis + "\n## Candidate patch\n```diff\n" + candidate.text + "\n```"));
				if (!verdict.accepted()) {
					candidate.rejections.add(role + ": " + verdict.reason());
				}
			}
			log("candidate from " + candidate.role + " drew " + candidate.rejections.size()
					+ " of " + CRITICS.size() + " rejections");
		}

		List<Candidate> survivors = new ArrayList<>();
		for (Candidate candidate : candidates) {
			if (candidate.rejections.size() < limits.criticQuorum()) {
				survivors.add(candidate);
			}
		}
		survivors.sort(Comparator.comparingInt(c -> c.rejections.size()));
		return survivors;
	}

	private LadderResult verify(Candidate candidate, TestResults baseline, FailureContext context)
			throws IOException, InterruptedException {

		log("verifying candidate from " + candidate.role);
		session.apply(candidate.patch);
		try {
			LadderResult verdict = ladder.run(baseline, context.getFailingTests());
			if (!verdict.passed()) {
				log("candidate from " + candidate.role + " failed at " + verdict.failedAt());
				session.revert();
			}
			return verdict;
		} catch (RuntimeException e) {
			// The workspace must not be left carrying a rejected patch, whatever
			// went wrong while checking it.
			session.revert();
			throw e;
		}
	}

	private String ask(AgentRole role, String system, String context) throws IOException {
		AiResult result = new Agent(provider, role, models, tools).ask(system, context);
		inputTokens += result.inputTokens();
		outputTokens += result.outputTokens();

		if (result.refused()) {
			log(role + " declined to answer (" + result.stopReason() + ")");
			return "";
		}
		return result.text();
	}

	private boolean overBudget() {
		return limits.maxTokens() > 0 && inputTokens + outputTokens >= limits.maxTokens();
	}

	private HealOutcome outcome(HealOutcome.Status status, String delivered, String detail) {
		return new HealOutcome(status, delivered, detail, inputTokens, outputTokens);
	}

	private void log(String message) {
		listener.getLogger().println("[p4-heal] " + message);
	}

	private static String firstLine(String text) {
		return StringUtils.left(StringUtils.substringBefore(text, "\n"), 120);
	}

	/**
	 * Models wrap diffs in markdown fences often enough to be worth handling
	 * rather than losing an otherwise good candidate to it.
	 */
	private static String stripFences(String answer) {
		String text = answer == null ? "" : answer.trim();
		if ("none".equalsIgnoreCase(text)) {
			return "";
		}
		if (!text.startsWith("```")) {
			return text;
		}
		int firstNewline = text.indexOf('\n');
		if (firstNewline < 0) {
			return text;
		}
		String body = text.substring(firstNewline + 1);
		int closing = body.lastIndexOf("```");
		return closing < 0 ? body : body.substring(0, closing);
	}

	/**
	 * A candidate patch and what the critics made of it.
	 */
	private static final class Candidate {

		private final AgentRole role;
		private final UnifiedDiff patch;
		private final String text;
		private final List<String> rejections = new ArrayList<>();

		Candidate(AgentRole role, UnifiedDiff patch, String text) {
			this.role = role;
			this.patch = patch;
			this.text = text;
		}

		String objections() {
			return String.join("\n", rejections);
		}
	}
}
