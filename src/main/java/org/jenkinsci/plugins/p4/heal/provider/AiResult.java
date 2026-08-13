package org.jenkinsci.plugins.p4.heal.provider;

import java.io.Serial;
import java.io.Serializable;

/**
 * What a model returned, after any tool-calling loop inside the provider has run
 * to completion.
 *
 * <p>{@code refused} is separate from a transport failure on purpose. A model may
 * decline a request and still answer with HTTP 200 and an empty body, so callers
 * must branch on this rather than assuming text is present.
 *
 * <p>Token counts feed the per-build budget cap; a provider that cannot report
 * them should return zero rather than guess.
 *
 * @param text         the model's final answer, empty if it refused
 * @param refused      true if the model declined the request
 * @param stopReason   provider-specific reason the turn ended, for logging
 * @param inputTokens  tokens consumed by the prompt across the whole loop
 * @param outputTokens tokens generated across the whole loop
 */
public record AiResult(String text, boolean refused, String stopReason,
                       long inputTokens, long outputTokens) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * @param text         the model's answer
	 * @param inputTokens  prompt tokens consumed
	 * @param outputTokens tokens generated
	 * @return a successful result
	 */
	public static AiResult of(String text, long inputTokens, long outputTokens) {
		return new AiResult(text, false, "end_turn", inputTokens, outputTokens);
	}

	/**
	 * @param stopReason   why the model declined
	 * @param inputTokens  prompt tokens consumed
	 * @param outputTokens tokens generated
	 * @return a refusal, carrying no usable text
	 */
	public static AiResult refusal(String stopReason, long inputTokens, long outputTokens) {
		return new AiResult("", true, stopReason, inputTokens, outputTokens);
	}
}
