package org.jenkinsci.plugins.p4.heal.provider;

import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and reads Anthropic Messages API payloads.
 *
 * <p>Kept separate from the HTTP call so the wire format is unit-testable without
 * a network, which is where the failure modes actually live: a parameter the
 * current models reject, a tool result returned in the wrong shape, or a refusal
 * read as an answer.
 */
final class ClaudeWire {

	private static final String CACHE_EPHEMERAL = "ephemeral";

	private ClaudeWire() {
	}

	/**
	 * Build a request body.
	 *
	 * <p>The system prompt carries a cache breakpoint: it holds the repository
	 * guidelines and failure context, which are identical across every agent in a
	 * heal run, so the prefix is written once and read thereafter.
	 *
	 * <p>Sampling parameters and {@code budget_tokens} are deliberately absent —
	 * current models reject them outright. Reasoning depth is set through
	 * {@code output_config.effort}, and thinking is on by default.
	 *
	 * <p>A second breakpoint is placed on the last message of the conversation. A
	 * tool-calling agent re-sends every earlier turn on each round trip, and the
	 * tool results are the bulk of it, so without this the same bytes are billed at
	 * full price once per remaining turn.
	 *
	 * @param request  what to ask
	 * @param tools    tools to offer; omitted from the body when empty, since an
	 *                 empty array is not a valid request
	 * @param messages the conversation so far, which the caller seeds and appends to
	 * @return the request body
	 */
	static JSONObject requestBody(AiRequest request, List<AiTool> tools, JSONArray messages) {
		JSONObject system = new JSONObject()
				.put("type", "text")
				.put("text", request.system())
				.put("cache_control", new JSONObject().put("type", CACHE_EPHEMERAL));

		JSONArray conversation = new JSONArray();
		for (int i = 0; i < messages.length(); i++) {
			conversation.put(messages.get(i));
		}
		markCacheable(conversation);

		JSONObject body = new JSONObject()
				.put("model", request.model())
				.put("max_tokens", request.maxTokens())
				.put("system", new JSONArray().put(system))
				.put("messages", conversation);

		if (request.effort() != null && !request.effort().isBlank()) {
			body.put("output_config", new JSONObject().put("effort", request.effort()));
		}

		if (!tools.isEmpty()) {
			JSONArray specs = new JSONArray();
			for (AiTool tool : tools) {
				specs.put(new JSONObject()
						.put("name", tool.getName())
						.put("description", tool.getDescription())
						.put("input_schema", new JSONObject(tool.getInputSchema())));
			}
			body.put("tools", specs);
		}
		return body;
	}

	/**
	 * Move the conversation's cache breakpoint to its last content block, so the
	 * next turn reads everything up to here instead of re-sending it at full price.
	 *
	 * <p>Any earlier breakpoint is cleared first. The caller holds the message
	 * objects and appends to them across turns, so marking without clearing would
	 * leave one breakpoint per turn and eventually exceed the four a request may
	 * carry. A cleared prefix is still cached — a later breakpoint covers everything
	 * before it.
	 *
	 * <p>Messages that carry a plain string rather than a content array — the user
	 * message that opens a conversation — have nowhere to put a breakpoint and are
	 * skipped; the system prompt already covers that much.
	 */
	private static void markCacheable(JSONArray conversation) {
		JSONObject last = null;

		for (int i = 0; i < conversation.length(); i++) {
			JSONObject message = conversation.optJSONObject(i);
			JSONArray content = message == null ? null : message.optJSONArray("content");
			if (content == null || content.isEmpty()) {
				continue;
			}
			for (int b = 0; b < content.length(); b++) {
				JSONObject block = content.optJSONObject(b);
				if (block != null) {
					block.remove("cache_control");
				}
			}
			last = content.optJSONObject(content.length() - 1);
		}

		if (last != null) {
			last.put("cache_control", new JSONObject().put("type", CACHE_EPHEMERAL));
		}
	}

	/**
	 * Decode one assistant turn.
	 *
	 * @param response the parsed response body
	 * @return the decoded turn
	 */
	static ClaudeTurn parseTurn(JSONObject response) {
		JSONArray content = response.has("content")
				? response.getJSONArray("content") : new JSONArray();

		StringBuilder text = new StringBuilder();
		List<ToolUse> toolUses = new ArrayList<>();

		for (int i = 0; i < content.length(); i++) {
			JSONObject block = content.getJSONObject(i);
			String type = block.optString("type", "");

			if ("text".equals(type)) {
				if (text.length() > 0) {
					text.append('\n');
				}
				text.append(block.optString("text", ""));
			} else if ("tool_use".equals(type)) {
				JSONObject input = block.optJSONObject("input");
				toolUses.add(new ToolUse(
						block.optString("id", ""),
						block.optString("name", ""),
						input == null ? "{}" : input.toString()));
			}
		}

		JSONObject usage = response.optJSONObject("usage");
		long inputTokens = usage == null ? 0 : usage.optLong("input_tokens", 0);
		long outputTokens = usage == null ? 0 : usage.optLong("output_tokens", 0);

		return new ClaudeTurn(text.toString(), toolUses,
				response.optString("stop_reason", ""), inputTokens, outputTokens, content);
	}

	/**
	 * Echo an assistant turn back into the conversation, unchanged.
	 *
	 * @param content the content array as received
	 * @return the assistant message to append
	 */
	static JSONObject assistantMessage(JSONArray content) {
		return new JSONObject().put("role", "assistant").put("content", content);
	}

	/**
	 * Return every tool result in a single user message.
	 *
	 * <p>Splitting results across several messages trains the model to stop issuing
	 * parallel tool calls, so they are always batched, and a failed tool is
	 * reported with {@code is_error} rather than dropped.
	 *
	 * @param outcomes results for every tool the model requested this turn
	 * @return the user message to append
	 */
	static JSONObject toolResultMessage(List<ToolOutcome> outcomes) {
		JSONArray content = new JSONArray();
		for (ToolOutcome outcome : outcomes) {
			content.put(new JSONObject()
					.put("type", "tool_result")
					.put("tool_use_id", outcome.toolUseId())
					.put("content", outcome.content())
					.put("is_error", outcome.error()));
		}
		return new JSONObject().put("role", "user").put("content", content);
	}
}
