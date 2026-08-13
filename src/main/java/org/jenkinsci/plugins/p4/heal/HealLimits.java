package org.jenkinsci.plugins.p4.heal;

import java.io.Serial;
import java.io.Serializable;

/**
 * Bounds on a heal run.
 *
 * <p>Every one of these exists to stop an automated loop spending without end.
 * A heal that cannot succeed should give up cheaply and leave the build red,
 * which is the honest outcome.
 *
 * <p>Agents run one at a time. The wall-clock cost of a heal is dominated by the
 * verify ladder — a full build and test run, repeated for the flake check — not
 * by the model calls, so running agents concurrently would save little and would
 * mean managing a thread pool inside a build step.
 *
 * @param maxAttempts  how many diagnose-generate-critique-verify rounds to try
 * @param criticQuorum how many of the four critics must reject a candidate for
 *                     it to be dropped
 * @param maxTokens    total model tokens across the run; 0 means no cap
 */
public record HealLimits(int maxAttempts, int criticQuorum, long maxTokens)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * @return the defaults: three attempts, a simple majority of critics, no token
	 *         cap
	 */
	public static HealLimits defaults() {
		return new HealLimits(3, 2, 0);
	}
}
