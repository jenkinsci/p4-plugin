package org.jenkinsci.plugins.p4.heal.agent;

import java.io.Serial;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One critic's judgement on a candidate patch.
 *
 * <p>Parsing fails closed. A critic whose answer cannot be read — it rambled, it
 * was cut off, it never stated a verdict — counts as a rejection, never as
 * approval. Treating an unreadable answer as consent would let a patch through
 * on the strength of a formatting accident.
 *
 * @param accepted whether this critic approved the patch
 * @param reason   the critic's explanation, fed back to the model on rejection
 */
public record Verdict(boolean accepted, String reason) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Pattern VERDICT_LINE =
			Pattern.compile("(?im)^\\W*verdict\\s*:\\s*(accept|reject)\\b");

	private static final String NO_VERDICT =
			"No verdict could be read from this critic's answer, so it counts as a rejection.";

	/**
	 * Read a critic's answer.
	 *
	 * @param answer the model's raw text
	 * @return the verdict; a rejection if none could be read
	 */
	public static Verdict parse(String answer) {
		if (answer == null || answer.isBlank()) {
			return new Verdict(false, NO_VERDICT);
		}

		Matcher matcher = VERDICT_LINE.matcher(answer);
		boolean sawAccept = false;
		boolean sawReject = false;
		int reasonFrom = -1;

		while (matcher.find()) {
			if ("reject".equalsIgnoreCase(matcher.group(1))) {
				sawReject = true;
			} else {
				sawAccept = true;
			}
			reasonFrom = matcher.end();
		}

		if (!sawAccept && !sawReject) {
			return new Verdict(false, NO_VERDICT);
		}

		String reason = reasonFrom < 0 ? "" : answer.substring(reasonFrom).trim();
		// A critic that manages to say both has not approved anything.
		return new Verdict(sawAccept && !sawReject, reason);
	}
}
