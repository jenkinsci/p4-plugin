package org.jenkinsci.plugins.p4.heal.provider;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class AiProviderTest {

	private static final AiRequest REQUEST =
			new AiRequest("system", "user", "test-model", 1024, "high");

	/**
	 * A read-only tool that records what it was asked, standing in for the real
	 * read_file / grep / glob tools.
	 */
	private static final class EchoTool implements AiTool {

		@Override
		public String getName() {
			return "echo";
		}

		@Override
		public String getDescription() {
			return "Echo the input back.";
		}

		@Override
		public String getInputSchema() {
			return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}";
		}

		@Override
		public String execute(String jsonInput) {
			return "echoed:" + jsonInput;
		}
	}

	@Test
	void testExtensionPointIsDiscoverable(JenkinsRule jenkins) {
		assertTrue(AiProvider.all().stream().anyMatch(d -> d.clazz.equals(FakeAiProvider.class)),
				"expected FakeAiProvider to be a registered AiProvider, found "
						+ AiProvider.all().stream().map(d -> d.clazz.getSimpleName()).toList());
	}

	@Test
	void testDescriptorResolvesFromInstance(JenkinsRule jenkins) {
		assertEquals("Fake AI provider (testing only)",
				new FakeAiProvider().getDescriptor().getDisplayName());
	}

	@Test
	void testConverseReturnsScriptedAnswer() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("first", "second");

		assertEquals("first", provider.converse(REQUEST, List.of()).text());
		assertEquals("second", provider.converse(REQUEST, List.of()).text());
	}

	@Test
	void testConverseRecordsRequests() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("answer");

		provider.converse(REQUEST, List.of());

		assertEquals(1, provider.getRequests().size());
		assertEquals("user", provider.getRequests().get(0).user());
		assertEquals("high", provider.getRequests().get(0).effort());
	}

	@Test
	void testConverseCanInvokeTools() throws Exception {
		FakeAiProvider provider = new FakeAiProvider()
				.willCallTool("echo", "{\"text\":\"hi\"}")
				.willSay("done");

		AiResult result = provider.converse(REQUEST, List.of(new EchoTool()));

		assertEquals("done", result.text());
		assertEquals(List.of("echoed:{\"text\":\"hi\"}"), provider.getToolInvocations());
	}

	@Test
	void testUnscriptedCallFailsLoudly() {
		FakeAiProvider provider = new FakeAiProvider();

		assertThrows(IOException.class, () -> provider.converse(REQUEST, List.of()));
	}

	@Test
	void testTransportFailurePropagates() {
		FakeAiProvider provider = new FakeAiProvider().willFail(new IOException("connection reset"));

		IOException thrown = assertThrows(IOException.class,
				() -> provider.converse(REQUEST, List.of()));
		assertEquals("connection reset", thrown.getMessage());
	}

	@Test
	void testResultFactoriesDistinguishAnswerFromRefusal() {
		AiResult answer = AiResult.of("text", 10, 5);
		AiResult refusal = AiResult.refusal("refusal", 10, 0);

		assertFalse(answer.refused());
		assertEquals(10, answer.inputTokens());
		assertEquals(5, answer.outputTokens());

		assertTrue(refusal.refused());
		assertEquals("", refusal.text());
	}
}
