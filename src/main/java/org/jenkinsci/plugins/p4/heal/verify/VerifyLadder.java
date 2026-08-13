package org.jenkinsci.plugins.p4.heal.verify;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rungs 4 to 8: the gates that decide whether a candidate patch is real.
 *
 * <p>Runs against a workspace that already has the patch applied. Applying and
 * reverting belong to the caller, so this class can be exercised without a
 * Perforce server.
 *
 * <p>The rungs answer four separate questions, and skipping any of them lets a
 * bad patch through: does it build, does it fix what was broken, does it break
 * anything that worked, and does it hold up when run more than once.
 */
public class VerifyLadder {

	private final BuildVerifier verifier;
	private final VerifyConfig config;

	/**
	 * @param verifier runs commands in the patched workspace
	 * @param config   commands and limits to use
	 */
	public VerifyLadder(BuildVerifier verifier, VerifyConfig config) {
		this.verifier = verifier;
		this.config = config;
	}

	/**
	 * Put a patched workspace through every gate.
	 *
	 * @param baseline test results from before the patch, taken from the failed
	 *                 build's own reports
	 * @param mustPass tests that were failing and therefore have to pass now
	 * @return a passing verdict only if every rung cleared
	 * @throws InterruptedException if the build was cancelled
	 */
	public LadderResult run(TestResults baseline, Set<String> mustPass) throws InterruptedException {
		try {
			Optional<LadderResult> compiled = compiles();
			if (compiled.isPresent()) {
				return compiled.get();
			}

			Optional<LadderResult> targeted = fixesWhatWasBroken(mustPass);
			if (targeted.isPresent()) {
				return targeted.get();
			}

			CommandResult verify = verifier.run(config.verifyCommand());
			if (!verify.ok()) {
				return LadderResult.fail(Rung.FULL_VERIFY, verify.output());
			}

			Optional<LadderResult> regressions = breaksNothingElse(baseline);
			if (regressions.isPresent()) {
				return regressions.get();
			}

			return holdsUpOnRepeat();

		} catch (IOException e) {
			// The agent went away, or a command could not be launched. That is not a
			// verdict on the patch, so it is reported as an error rather than a
			// rejection.
			return LadderResult.fail(Rung.ERROR, "Verification could not run: " + e.getMessage());
		}
	}

	/**
	 * Each rung returns the failure that ends the run, or empty when it cleared and
	 * the next rung should run. Empty rather than a passing {@link LadderResult}, so
	 * there is only one thing in this class that means "the whole ladder passed".
	 */
	private Optional<LadderResult> compiles() throws IOException, InterruptedException {
		CommandResult compile = verifier.run(config.compileCommand());
		return compile.ok()
				? Optional.empty() : Optional.of(LadderResult.fail(Rung.COMPILE, compile.output()));
	}

	private Optional<LadderResult> fixesWhatWasBroken(Set<String> mustPass)
			throws IOException, InterruptedException {

		if (config.targetedTestCommand() == null || config.targetedTestCommand().isBlank()
				|| mustPass.isEmpty()) {
			return Optional.empty();
		}

		String ids = String.join(",", new TreeSet<>(mustPass));
		CommandResult targeted = verifier.run(config.targetedCommandFor(ids));

		Set<String> stillBroken = new TreeSet<>(mustPass);
		stillBroken.removeAll(verifier.readTestResults().getPassed());

		if (stillBroken.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(LadderResult.fail(Rung.TARGETED_TESTS,
				"These tests are still not passing: " + stillBroken + "\n" + targeted.output()));
	}

	private Optional<LadderResult> breaksNothingElse(TestResults baseline)
			throws IOException, InterruptedException {

		TestResults after = verifier.readTestResults();

		Set<String> regressions = after.newlyFailingSince(baseline);
		if (!regressions.isEmpty()) {
			StringBuilder detail = new StringBuilder(
					"The patch broke tests that passed before it: " + regressions + "\n");
			for (String test : regressions) {
				detail.append(test).append('\n').append(after.getFailureDetail(test)).append('\n');
			}
			return Optional.of(LadderResult.fail(Rung.REGRESSION, detail.toString()));
		}

		Set<String> unfixed = after.stillFailingSince(baseline);
		if (!unfixed.isEmpty()) {
			// Covers a test that now merely fails to run: only an actual pass counts.
			return Optional.of(LadderResult.fail(Rung.UNFIXED,
					"These tests were failing before the patch and still are not passing: " + unfixed));
		}
		return Optional.empty();
	}

	private LadderResult holdsUpOnRepeat() throws IOException, InterruptedException {
		for (int attempt = 1; attempt <= config.flakeReruns(); attempt++) {
			CommandResult rerun = verifier.run(config.verifyCommand());
			if (!rerun.ok()) {
				return LadderResult.fail(Rung.FLAKE, "Re-run " + attempt + " of "
						+ config.flakeReruns() + " failed, so the fix is not reliable:\n"
						+ rerun.output());
			}
		}
		return LadderResult.pass();
	}
}
