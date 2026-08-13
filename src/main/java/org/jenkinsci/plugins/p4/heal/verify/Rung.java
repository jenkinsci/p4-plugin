package org.jenkinsci.plugins.p4.heal.verify;

/**
 * The gates a candidate patch must clear, in order.
 *
 * <p>Each is a hard stop. The ladder exists because a model's confidence that a
 * patch is correct is not evidence: only the build is.
 */
public enum Rung {

	/**
	 * The patch compiles.
	 */
	COMPILE,

	/**
	 * The tests that were failing now pass.
	 */
	TARGETED_TESTS,

	/**
	 * The whole build-and-test command succeeds.
	 */
	FULL_VERIFY,

	/**
	 * Nothing that passed before the patch fails after it.
	 */
	REGRESSION,

	/**
	 * Every test the build started out failing is genuinely fixed, not merely
	 * absent or skipped.
	 */
	UNFIXED,

	/**
	 * Repeated runs agree. One green run of a nondeterministic suite proves
	 * nothing.
	 */
	FLAKE,

	/**
	 * Verification could not be carried out — the agent went away, a command could
	 * not be launched. Distinct from a patch that was tried and rejected.
	 */
	ERROR
}
