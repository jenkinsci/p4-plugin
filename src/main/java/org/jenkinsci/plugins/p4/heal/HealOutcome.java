package org.jenkinsci.plugins.p4.heal;

import java.io.Serial;
import java.io.Serializable;

/**
 * How a heal run ended.
 *
 * @param status       what happened
 * @param delivered    reference to the delivered patch, empty unless healed
 * @param detail       what to print on the build console
 * @param inputTokens  model input tokens consumed
 * @param outputTokens model output tokens consumed
 */
public record HealOutcome(Status status, String delivered, String detail,
                          long inputTokens, long outputTokens) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * The possible endings, kept distinct because they mean different things to
	 * whoever reads the build afterwards.
	 */
	public enum Status {

		/**
		 * A patch cleared every gate and was delivered.
		 */
		HEALED,

		/**
		 * The model produced nothing that parsed as a patch.
		 */
		NO_CANDIDATE,

		/**
		 * Every candidate was rejected before it reached the build — by the guard,
		 * or by the critics.
		 */
		ALL_REJECTED,

		/**
		 * Candidates were built and tried, and none survived compilation or the
		 * tests. The honest outcome for a failure the model could not fix.
		 */
		VERIFICATION_FAILED,

		/**
		 * The run hit its token ceiling before reaching a verdict.
		 */
		BUDGET_EXHAUSTED,

		/**
		 * The model or the workspace could not be reached. Says nothing about
		 * whether the build was fixable.
		 */
		ERROR
	}

	/**
	 * @return true if a verified patch was delivered
	 */
	public boolean healed() {
		return status == Status.HEALED;
	}
}
