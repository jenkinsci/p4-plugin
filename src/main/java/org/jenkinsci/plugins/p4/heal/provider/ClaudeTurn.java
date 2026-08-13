package org.jenkinsci.plugins.p4.heal.provider;

import kong.unirest.core.json.JSONArray;

import java.util.List;

/**
 * One assistant turn, decoded from a Messages API response.
 *
 * @param text         concatenated text blocks; empty when the model only asked
 *                     for tools, or refused
 * @param toolUses     tools the model wants run before it will answer
 * @param stopReason   why the turn ended
 * @param inputTokens  prompt tokens billed for this turn
 * @param outputTokens tokens generated in this turn
 * @param rawContent   the content array exactly as received, to be echoed back on
 *                     the next request; reconstructing it would drop block types
 *                     the plugin does not model
 */
public record ClaudeTurn(String text, List<ToolUse> toolUses, String stopReason,
                         long inputTokens, long outputTokens, JSONArray rawContent) {

	/**
	 * @return true if the model declined the request; the response carries HTTP 200
	 *         and no usable text, so callers must check this before reading
	 *         {@link #text()}
	 */
	public boolean refused() {
		return "refusal".equals(stopReason);
	}

	/**
	 * @return true if the conversation must continue with tool results
	 */
	public boolean wantsTools() {
		return !toolUses.isEmpty();
	}
}
