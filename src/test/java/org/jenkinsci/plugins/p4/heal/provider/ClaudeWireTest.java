package org.jenkinsci.plugins.p4.heal.provider;

import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeWireTest {

	private static final AiRequest REQUEST =
			new AiRequest("guidelines here", "fix this", "claude-opus-5", 16000, "xhigh");

	private static final class StubTool implements AiTool {

		@Override
		public String getName() {
			return "read_file";
		}

		@Override
		public String getDescription() {
			return "Read a file from the workspace.";
		}

		@Override
		public String getInputSchema() {
			return "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},"
					+ "\"required\":[\"path\"]}";
		}

		@Override
		public String execute(String jsonInput) {
			return "contents";
		}
	}

	private static JSONObject body() {
		return ClaudeWire.requestBody(REQUEST, List.of(new StubTool()), new JSONArray());
	}

	@Test
	void testCarriesModelAndTokenCeiling() {
		JSONObject body = body();

		assertEquals("claude-opus-5", body.getString("model"));
		assertEquals(16000, body.getInt("max_tokens"));
	}

	@Test
	void testSystemPromptIsCacheable() {
		JSONArray system = body().getJSONArray("system");

		assertEquals(1, system.length());
		JSONObject block = system.getJSONObject(0);
		assertEquals("text", block.getString("type"));
		assertEquals("guidelines here", block.getString("text"));
		assertEquals("ephemeral", block.getJSONObject("cache_control").getString("type"));
	}

	@Test
	void testEffortIsNestedInsideOutputConfig() {
		JSONObject body = body();

		assertFalse(body.has("effort"), "effort is not a top-level parameter");
		assertEquals("xhigh", body.getJSONObject("output_config").getString("effort"));
	}

	@Test
	void testOmitsParametersThatAreRejectedByCurrentModels() {
		JSONObject body = body();

		assertFalse(body.has("temperature"), "temperature returns 400 on Opus 5");
		assertFalse(body.has("top_p"), "top_p returns 400 on Opus 5");
		assertFalse(body.has("top_k"), "top_k returns 400 on Opus 5");
		assertFalse(body.has("budget_tokens"), "budget_tokens returns 400 on Opus 5");
		assertFalse(body.toString().contains("budget_tokens"));
	}

	@Test
	void testSerialisesTools() {
		JSONArray tools = body().getJSONArray("tools");

		assertEquals(1, tools.length());
		JSONObject tool = tools.getJSONObject(0);
		assertEquals("read_file", tool.getString("name"));
		assertEquals("Read a file from the workspace.", tool.getString("description"));
		assertEquals("string",
				tool.getJSONObject("input_schema").getJSONObject("properties")
						.getJSONObject("path").getString("type"));
	}

	@Test
	void testOmitsToolsKeyWhenThereAreNone() {
		JSONObject body = ClaudeWire.requestBody(REQUEST, List.of(), new JSONArray());

		assertFalse(body.has("tools"), "an empty tools array is not a valid request");
	}

	@Test
	void testSendsTheConversationExactlyAsTheCallerBuiltIt() {
		// Seeding is the caller's job — ClaudeProviderImpl puts the user turn in before
		// the first request — so an empty conversation stays empty rather than being
		// filled in from the request in a second place.
		assertTrue(body().getJSONArray("messages").isEmpty());
	}

	@Test
	void testTheLatestTurnCarriesTheCacheBreakpoint() {
		JSONArray conversation = new JSONArray();
		conversation.put(new JSONObject().put("role", "user")
				.put("content", new JSONArray().put(new JSONObject()
						.put("type", "text").put("text", "earlier"))));
		conversation.put(new JSONObject().put("role", "assistant")
				.put("content", new JSONArray().put(new JSONObject()
						.put("type", "text").put("text", "latest"))));

		JSONArray messages = ClaudeWire.requestBody(REQUEST, List.of(), conversation)
				.getJSONArray("messages");

		JSONArray first = messages.getJSONObject(0).getJSONArray("content");
		assertFalse(first.getJSONObject(0).has("cache_control"),
				"only the last block is marked; an earlier one is covered by it");

		JSONArray last = messages.getJSONObject(1).getJSONArray("content");
		assertEquals("ephemeral",
				last.getJSONObject(0).getJSONObject("cache_control").getString("type"));
	}

	@Test
	void testTheBreakpointMovesRatherThanAccumulating() {
		// The caller keeps appending to one array across turns, so marking without
		// clearing would leave a breakpoint per turn and blow the four-block limit.
		JSONArray conversation = new JSONArray();
		conversation.put(new JSONObject().put("role", "user")
				.put("content", new JSONArray().put(new JSONObject()
						.put("type", "text").put("text", "turn one"))));

		ClaudeWire.requestBody(REQUEST, List.of(), conversation);

		conversation.put(new JSONObject().put("role", "assistant")
				.put("content", new JSONArray().put(new JSONObject()
						.put("type", "text").put("text", "turn two"))));

		JSONArray messages = ClaudeWire.requestBody(REQUEST, List.of(), conversation)
				.getJSONArray("messages");

		assertFalse(messages.getJSONObject(0).getJSONArray("content")
				.getJSONObject(0).has("cache_control"), "the old breakpoint must be cleared");
		assertTrue(messages.getJSONObject(1).getJSONArray("content")
				.getJSONObject(0).has("cache_control"));
	}

	@Test
	void testKeepsAnExistingConversation() {
		JSONArray existing = new JSONArray();
		existing.put(new JSONObject().put("role", "user").put("content", "earlier"));

		JSONArray messages = ClaudeWire.requestBody(REQUEST, List.of(), existing)
				.getJSONArray("messages");

		assertEquals(1, messages.length());
		assertEquals("earlier", messages.getJSONObject(0).getString("content"));
	}

	@Test
	void testParsesTextAnswer() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "end_turn")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "text").put("text", "the answer")))
				.put("usage", new JSONObject().put("input_tokens", 120).put("output_tokens", 34));

		ClaudeTurn turn = ClaudeWire.parseTurn(response);

		assertEquals("the answer", turn.text());
		assertEquals("end_turn", turn.stopReason());
		assertEquals(120, turn.inputTokens());
		assertEquals(34, turn.outputTokens());
		assertTrue(turn.toolUses().isEmpty());
	}

	@Test
	void testJoinsMultipleTextBlocks() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "end_turn")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "text").put("text", "first"))
						.put(new JSONObject().put("type", "text").put("text", "second")));

		assertEquals("first\nsecond", ClaudeWire.parseTurn(response).text());
	}

	@Test
	void testIgnoresThinkingBlocks() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "end_turn")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "thinking").put("thinking", ""))
						.put(new JSONObject().put("type", "text").put("text", "answer")));

		assertEquals("answer", ClaudeWire.parseTurn(response).text());
	}

	@Test
	void testParsesToolUseBlocks() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "tool_use")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "tool_use")
								.put("id", "toolu_1").put("name", "read_file")
								.put("input", new JSONObject().put("path", "Foo.java"))));

		ClaudeTurn turn = ClaudeWire.parseTurn(response);

		assertEquals(1, turn.toolUses().size());
		assertEquals("toolu_1", turn.toolUses().get(0).id());
		assertEquals("read_file", turn.toolUses().get(0).name());
		assertTrue(turn.toolUses().get(0).input().contains("Foo.java"));
		assertTrue(turn.wantsTools());
	}

	@Test
	void testDetectsRefusal() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "refusal")
				.put("content", new JSONArray());

		ClaudeTurn turn = ClaudeWire.parseTurn(response);

		assertTrue(turn.refused());
		assertEquals("", turn.text());
	}

	@Test
	void testToleratesMissingUsage() {
		JSONObject response = new JSONObject()
				.put("stop_reason", "end_turn")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "text").put("text", "hi")));

		ClaudeTurn turn = ClaudeWire.parseTurn(response);

		assertEquals(0, turn.inputTokens());
		assertEquals(0, turn.outputTokens());
	}

	@Test
	void testAllToolResultsGoBackInOneUserMessage() {
		JSONObject message = ClaudeWire.toolResultMessage(List.of(
				new ToolOutcome("toolu_1", "first result", false),
				new ToolOutcome("toolu_2", "second result", true)));

		assertEquals("user", message.getString("role"));
		JSONArray content = message.getJSONArray("content");
		assertEquals(2, content.length(), "splitting results across messages suppresses"
				+ " parallel tool use on later turns");

		assertEquals("tool_result", content.getJSONObject(0).getString("type"));
		assertEquals("toolu_1", content.getJSONObject(0).getString("tool_use_id"));
		assertFalse(content.getJSONObject(0).getBoolean("is_error"));
		assertTrue(content.getJSONObject(1).getBoolean("is_error"));
	}

	@Test
	void testAssistantTurnIsEchoedBackVerbatim() {
		JSONArray content = new JSONArray()
				.put(new JSONObject().put("type", "tool_use").put("id", "toolu_1")
						.put("name", "read_file").put("input", new JSONObject()));

		JSONObject message = ClaudeWire.assistantMessage(content);

		assertEquals("assistant", message.getString("role"));
		assertEquals(content.toString(), message.getJSONArray("content").toString());
	}
}
