package org.jenkinsci.plugins.p4.heal.provider;

import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeProviderImplTest {

	private static final AiRequest REQUEST =
			new AiRequest("system", "fix the build", "claude-opus-5", 16000, "xhigh");

	/**
	 * Stubs the one HTTP call so the tool loop — the part most likely to be wrong —
	 * is exercised without a network.
	 */
	private static class StubbedProvider extends ClaudeProviderImpl {

		private final Deque<JSONObject> responses = new ArrayDeque<>();
		final List<JSONObject> sent = new ArrayList<>();
		private final List<ClassLoader> loaders = new ArrayList<>();

		StubbedProvider(JSONObject... scripted) {
			super("cred-id");
			for (JSONObject response : scripted) {
				responses.add(response);
			}
		}

		@Override
		protected JSONObject send(JSONObject body) throws IOException {
			sent.add(body);
			loaders.add(Thread.currentThread().getContextClassLoader());
			if (responses.isEmpty()) {
				throw new IOException("no scripted response for request " + (sent.size()));
			}
			return responses.removeFirst();
		}
	}

	/**
	 * Rejects any request carrying an effort, the way the API rejects one sent to a
	 * model that has no such parameter.
	 */
	private static final class EffortRejectingProvider extends StubbedProvider {

		EffortRejectingProvider(JSONObject... scripted) {
			super(scripted);
		}

		@Override
		protected JSONObject send(JSONObject body) throws IOException {
			if (body.has("output_config")) {
				sent.add(body);
				throw new IOException("Anthropic API returned 400:"
						+ " This model does not support the effort parameter.");
			}
			return super.send(body);
		}
	}

	private static JSONObject answer(String text, long in, long out) {
		return new JSONObject()
				.put("stop_reason", "end_turn")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "text").put("text", text)))
				.put("usage", new JSONObject().put("input_tokens", in).put("output_tokens", out));
	}

	private static JSONObject wantsTool(String id, String name, JSONObject input) {
		return new JSONObject()
				.put("stop_reason", "tool_use")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "tool_use")
								.put("id", id).put("name", name).put("input", input)))
				.put("usage", new JSONObject().put("input_tokens", 10).put("output_tokens", 5));
	}

	private static final class RecordingTool implements AiTool {

		private final String name;
		private final List<String> calls = new ArrayList<>();
		private final IOException failure;

		RecordingTool(String name) {
			this(name, null);
		}

		RecordingTool(String name, IOException failure) {
			this.name = name;
			this.failure = failure;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return "test tool";
		}

		@Override
		public String getInputSchema() {
			return "{\"type\":\"object\",\"properties\":{}}";
		}

		@Override
		public String execute(String jsonInput) throws IOException {
			calls.add(jsonInput);
			if (failure != null) {
				throw failure;
			}
			return "result for " + jsonInput;
		}
	}

	@Test
	void testReturnsPlainAnswer() throws Exception {
		StubbedProvider provider = new StubbedProvider(answer("all done", 100, 20));

		AiResult result = provider.converse(REQUEST, List.of());

		assertEquals("all done", result.text());
		assertFalse(result.refused());
		assertEquals(100, result.inputTokens());
		assertEquals(20, result.outputTokens());
	}

	@Test
	void testSeedsConversationWithTheUserTurn() throws Exception {
		StubbedProvider provider = new StubbedProvider(answer("ok", 1, 1));

		provider.converse(REQUEST, List.of());

		JSONArray messages = provider.sent.get(0).getJSONArray("messages");
		assertEquals(1, messages.length());
		assertEquals("user", messages.getJSONObject(0).getString("role"));
		assertEquals("fix the build", messages.getJSONObject(0).getString("content"));
	}

	@Test
	void testRunsToolThenContinues() throws Exception {
		RecordingTool tool = new RecordingTool("read_file");
		StubbedProvider provider = new StubbedProvider(
				wantsTool("toolu_1", "read_file", new JSONObject().put("path", "Foo.java")),
				answer("fixed it", 200, 40));

		AiResult result = provider.converse(REQUEST, List.of(tool));

		assertEquals("fixed it", result.text());
		assertEquals(1, tool.calls.size());
		assertTrue(tool.calls.get(0).contains("Foo.java"));
	}

	@Test
	void testEchoesAssistantTurnAndReturnsResultsInOneMessage() throws Exception {
		RecordingTool tool = new RecordingTool("read_file");
		StubbedProvider provider = new StubbedProvider(
				wantsTool("toolu_1", "read_file", new JSONObject().put("path", "Foo.java")),
				answer("done", 1, 1));

		provider.converse(REQUEST, List.of(tool));

		JSONArray messages = provider.sent.get(1).getJSONArray("messages");
		assertEquals(3, messages.length());
		assertEquals("user", messages.getJSONObject(0).getString("role"));
		assertEquals("assistant", messages.getJSONObject(1).getString("role"));
		assertEquals("user", messages.getJSONObject(2).getString("role"));

		JSONArray results = messages.getJSONObject(2).getJSONArray("content");
		assertEquals(1, results.length());
		assertEquals("toolu_1", results.getJSONObject(0).getString("tool_use_id"));
	}

	@Test
	void testBatchesParallelToolCallsIntoOneMessage() throws Exception {
		RecordingTool one = new RecordingTool("read_file");
		RecordingTool two = new RecordingTool("grep");

		JSONObject parallel = new JSONObject()
				.put("stop_reason", "tool_use")
				.put("content", new JSONArray()
						.put(new JSONObject().put("type", "tool_use").put("id", "toolu_1")
								.put("name", "read_file").put("input", new JSONObject()))
						.put(new JSONObject().put("type", "tool_use").put("id", "toolu_2")
								.put("name", "grep").put("input", new JSONObject())))
				.put("usage", new JSONObject().put("input_tokens", 1).put("output_tokens", 1));

		StubbedProvider provider = new StubbedProvider(parallel, answer("done", 1, 1));

		provider.converse(REQUEST, List.of(one, two));

		assertEquals(1, one.calls.size());
		assertEquals(1, two.calls.size());

		JSONArray results = provider.sent.get(1).getJSONArray("messages")
				.getJSONObject(2).getJSONArray("content");
		assertEquals(2, results.length(), "both results belong in a single user message");
	}

	@Test
	void testAccumulatesTokensAcrossTheWholeLoop() throws Exception {
		StubbedProvider provider = new StubbedProvider(
				wantsTool("toolu_1", "read_file", new JSONObject()),
				answer("done", 200, 40));

		AiResult result = provider.converse(REQUEST, List.of(new RecordingTool("read_file")));

		assertEquals(210, result.inputTokens());
		assertEquals(45, result.outputTokens());
	}

	@Test
	void testRefusalIsReportedNotTreatedAsAnAnswer() throws Exception {
		JSONObject refusal = new JSONObject()
				.put("stop_reason", "refusal")
				.put("content", new JSONArray())
				.put("usage", new JSONObject().put("input_tokens", 5).put("output_tokens", 0));

		AiResult result = new StubbedProvider(refusal).converse(REQUEST, List.of());

		assertTrue(result.refused());
		assertEquals("", result.text());
		assertEquals("refusal", result.stopReason());
	}

	@Test
	void testFailingToolIsReportedBackRatherThanAbortingTheRun() throws Exception {
		RecordingTool broken = new RecordingTool("read_file", new IOException("file not found"));
		StubbedProvider provider = new StubbedProvider(
				wantsTool("toolu_1", "read_file", new JSONObject()),
				answer("recovered", 1, 1));

		AiResult result = provider.converse(REQUEST, List.of(broken));

		assertEquals("recovered", result.text());
		JSONObject toolResult = provider.sent.get(1).getJSONArray("messages")
				.getJSONObject(2).getJSONArray("content").getJSONObject(0);
		assertTrue(toolResult.getBoolean("is_error"));
		assertTrue(toolResult.getString("content").contains("file not found"));
	}

	@Test
	void testUnknownToolIsReportedBackAsAnError() throws Exception {
		StubbedProvider provider = new StubbedProvider(
				wantsTool("toolu_1", "no_such_tool", new JSONObject()),
				answer("recovered", 1, 1));

		provider.converse(REQUEST, List.of(new RecordingTool("read_file")));

		JSONObject toolResult = provider.sent.get(1).getJSONArray("messages")
				.getJSONObject(2).getJSONArray("content").getJSONObject(0);
		assertTrue(toolResult.getBoolean("is_error"));
		assertTrue(toolResult.getString("content").contains("no_such_tool"));
	}

	@Test
	void testRunawayToolLoopIsBounded() {
		JSONObject[] endless = new JSONObject[50];
		for (int i = 0; i < endless.length; i++) {
			endless[i] = wantsTool("toolu_" + i, "read_file", new JSONObject());
		}
		StubbedProvider provider = new StubbedProvider(endless);
		provider.setMaxToolIterations(5);

		IOException thrown = assertThrows(IOException.class,
				() -> provider.converse(REQUEST, List.of(new RecordingTool("read_file"))));

		assertTrue(thrown.getMessage().contains("5"), thrown.getMessage());
		assertEquals(5, provider.sent.size());
	}

	/**
	 * Unirest looks its JSON engine up with {@link java.util.ServiceLoader}, which
	 * reads the thread context class loader. A Jenkins build thread does not carry
	 * the plugin's own loader, so unless the call installs it the JSON classes fail
	 * to initialise and healing dies with
	 * "No Json Parsing Implementation Provided".
	 */
	@Test
	void testInstallsThePluginClassLoaderSoUnirestCanFindItsJsonEngine() throws Exception {
		StubbedProvider provider = new StubbedProvider(answer("all done", 1, 1));

		Thread current = Thread.currentThread();
		ClassLoader foreign = ClassLoader.getPlatformClassLoader();
		ClassLoader original = current.getContextClassLoader();
		current.setContextClassLoader(foreign);
		try {
			provider.converse(REQUEST, List.of());
		} finally {
			current.setContextClassLoader(original);
		}

		assertEquals(1, provider.loaders.size());
		assertSame(ClaudeProviderImpl.class.getClassLoader(), provider.loaders.get(0),
				"the call must run with the loader that bundles the JSON engine");
	}

	@Test
	void testRestoresTheCallersClassLoaderAfterwards() throws Exception {
		StubbedProvider provider = new StubbedProvider(answer("all done", 1, 1));

		Thread current = Thread.currentThread();
		ClassLoader foreign = ClassLoader.getPlatformClassLoader();
		ClassLoader original = current.getContextClassLoader();
		current.setContextClassLoader(foreign);
		try {
			provider.converse(REQUEST, List.of());
			assertSame(foreign, current.getContextClassLoader());
		} finally {
			current.setContextClassLoader(original);
		}
	}

	@Test
	void testRestoresTheCallersClassLoaderWhenTheCallFails() {
		StubbedProvider provider = new StubbedProvider();

		Thread current = Thread.currentThread();
		ClassLoader foreign = ClassLoader.getPlatformClassLoader();
		ClassLoader original = current.getContextClassLoader();
		current.setContextClassLoader(foreign);
		try {
			assertThrows(IOException.class, () -> provider.converse(REQUEST, List.of()));
			assertSame(foreign, current.getContextClassLoader());
		} finally {
			current.setContextClassLoader(original);
		}
	}

	@Test
	void testAKeyThatIsAlreadyAUsableHeaderValueIsLeftAlone() throws Exception {
		String key = "sk-ant-api03-AbC123_-xyz";

		assertEquals(key, ClaudeProviderImpl.usableKey(key));
	}

	@Test
	void testAnEmptyKeyIsRejectedBeforeTheRequestIsBuilt() {
		IOException thrown = assertThrows(IOException.class, () -> ClaudeProviderImpl.usableKey("  "));

		assertTrue(thrown.getMessage().contains("empty"), thrown.getMessage());
	}

	/**
	 * The JDK's own header check puts the offending value in its message, so a key
	 * with a stray newline would end up printed in the build log. The check has to
	 * happen here, and its message must not carry the value.
	 */
	@Test
	void testAKeyThatCannotBeSentAsAHeaderIsRejectedWithoutEchoingIt() {
		String pasted = "sk-ant-secret-part\nsecond line of whatever was on the clipboard";

		IOException thrown = assertThrows(IOException.class,
				() -> ClaudeProviderImpl.usableKey(pasted));

		assertFalse(thrown.getMessage().contains("sk-ant-secret-part"),
				"the key must never reach the build log: " + thrown.getMessage());
		assertFalse(thrown.getMessage().contains("second line"), thrown.getMessage());
	}

	@Test
	void testAKeyWithATrailingNewlineIsRejected() {
		assertThrows(IOException.class, () -> ClaudeProviderImpl.usableKey("sk-ant-api03-abc\n"));
	}

	@Test
	void testPastedProseIsRejected() {
		assertThrows(IOException.class,
				() -> ClaudeProviderImpl.usableKey("Started by user unknown or anonymous"));
	}

	/**
	 * Not every model accepts an effort — the cheap model the critics run on does
	 * not — and the rest of the request is fine, so a rejected effort is dropped and
	 * the call retried rather than failing the whole heal.
	 */
	@Test
	void testRetriesWithoutEffortWhenTheModelRejectsIt() throws Exception {
		EffortRejectingProvider provider = new EffortRejectingProvider(answer("all done", 100, 20));

		AiResult result = provider.converse(REQUEST, List.of());

		assertEquals("all done", result.text());
		assertEquals(2, provider.sent.size());
		assertTrue(provider.sent.get(0).has("output_config"));
		assertFalse(provider.sent.get(1).has("output_config"),
				"the retry must not carry the parameter the model just rejected");
	}

	/**
	 * The retry costs a round trip, so it is paid once per conversation rather than
	 * on every turn of the tool loop.
	 */
	@Test
	void testTheDroppedEffortStaysDroppedForTheRestOfTheConversation() throws Exception {
		EffortRejectingProvider provider = new EffortRejectingProvider(
				wantsTool("toolu_1", "read_file", new JSONObject()),
				answer("done", 1, 1));

		provider.converse(REQUEST, List.of(new RecordingTool("read_file")));

		assertEquals(3, provider.sent.size(), "one rejected call, then two clean ones");
		assertFalse(provider.sent.get(2).has("output_config"));
	}

	@Test
	void testAFailureUnrelatedToEffortIsNotRetried() {
		StubbedProvider provider = new StubbedProvider();

		assertThrows(IOException.class, () -> provider.converse(REQUEST, List.of()));

		assertEquals(1, provider.sent.size());
	}

	@Test
	void testDefaultsAreSensible() {
		ClaudeProviderImpl provider = new ClaudeProviderImpl("cred-id");

		assertEquals("cred-id", provider.getCredentialId());
		assertEquals("https://api.anthropic.com/v1/messages", provider.getEndpoint());
		assertTrue(provider.getMaxToolIterations() > 0);
		assertTrue(provider.getRequestTimeoutSeconds() > 0);
	}
}
