package org.jenkinsci.plugins.p4.heal.verify;

import java.io.Serial;
import java.io.Serializable;

/**
 * The verdict on one candidate patch.
 *
 * <p>{@code detail} is written to be read by the model on the next attempt, so it
 * carries the actual compiler or test output rather than a summary. Telling the
 * model only that verification failed wastes the retry.
 *
 * @param passed   true only if every rung was cleared
 * @param failedAt the rung that stopped it, null when it passed
 * @param detail   what went wrong, in enough detail to act on
 */
public record LadderResult(boolean passed, Rung failedAt, String detail) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * @return a passing verdict
	 */
	public static LadderResult pass() {
		return new LadderResult(true, null, "");
	}

	/**
	 * @param rung   where it stopped
	 * @param detail what went wrong
	 * @return a failing verdict
	 */
	public static LadderResult fail(Rung rung, String detail) {
		return new LadderResult(false, rung, detail == null ? "" : detail);
	}
}
