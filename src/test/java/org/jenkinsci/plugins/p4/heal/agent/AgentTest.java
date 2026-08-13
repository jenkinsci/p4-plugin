package org.jenkinsci.plugins.p4.heal.agent;

import org.jenkinsci.plugins.p4.heal.provider.AiRequest;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;
import org.jenkinsci.plugins.p4.heal.provider.FakeAiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

	private static final HealModels MODELS =
			new HealModels("claude-opus-5", "claude-haiku-4-5", "xhigh", "medium", 16000);

	private static final class NoopTool implements AiTool {

		@Override
		public String getName() {
			return "noop";
		}

		@Override
		public String getDescription() {
			return "does nothing";
		}

		@Override
		public String getInputSchema() {
			return "{\"type\":\"object\",\"properties\":{}}";
		}

		@Override
		public String execute(String jsonInput) {
			return "";
		}
	}

	@Test
	void testEveryRoleHasALoadablePrompt() {
		for (AgentRole role : AgentRole.values()) {
			String prompt = role.prompt();

			assertFalse(prompt.isBlank(), role + " has no prompt resource");
			assertTrue(prompt.length() > 100,
					role + " has a prompt too thin to steer a model: " + prompt.length() + " chars");
		}
	}

	@Test
	void testFixRolesDemandADiffAndNothingElse() {
		for (AgentRole role : AgentRole.values()) {
			if (!role.isFix()) {
				continue;
			}
			assertTrue(role.prompt().contains("unified diff"),
					role + " must state the output contract");
		}
	}

	@Test
	void testCriticRolesAskForAVerdictLine() {
		for (AgentRole role : AgentRole.values()) {
			if (!role.isCritic()) {
				continue;
			}
			assertTrue(role.prompt().contains("VERDICT:"),
					role + " must ask for a parseable verdict");
		}
	}

	@Test
	void testRolesAreClassifiedExclusively() {
		for (AgentRole role : AgentRole.values()) {
			assertFalse(role.isFix() && role.isCritic(), role + " cannot be both");
		}
		assertTrue(AgentRole.FIX_MINIMAL.isFix());
		assertTrue(AgentRole.CRITIC_CORRECTNESS.isCritic());
		assertFalse(AgentRole.DIAGNOSE_COMPILE.isFix());
		assertFalse(AgentRole.DIAGNOSE_COMPILE.isCritic());
	}

	@Test
	void testAskSendsTheRolePromptWithTheContext() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("answer");
		Agent agent = new Agent(provider, AgentRole.FIX_MINIMAL, MODELS, List.of());

		agent.ask("the guidelines", "the failure context");

		AiRequest sent = provider.getRequests().get(0);
		assertEquals("the guidelines", sent.system());
		assertTrue(sent.user().contains("the failure context"), sent.user());
		assertTrue(sent.user().contains(AgentRole.FIX_MINIMAL.prompt()), "role prompt is missing");
	}

	@Test
	void testExpensiveWorkGoesToTheCapableModel() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("answer");

		new Agent(provider, AgentRole.FIX_ROOT_CAUSE, MODELS, List.of()).ask("s", "c");

		assertEquals("claude-opus-5", provider.getRequests().get(0).model());
		assertEquals("xhigh", provider.getRequests().get(0).effort());
	}

	@Test
	void testCriticsGoToTheCheapModel() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("answer");

		new Agent(provider, AgentRole.CRITIC_REGRESSION, MODELS, List.of()).ask("s", "c");

		assertEquals("claude-haiku-4-5", provider.getRequests().get(0).model());
		assertEquals("medium", provider.getRequests().get(0).effort());
	}

	@Test
	void testToolsArePassedThrough() throws Exception {
		FakeAiProvider provider = new FakeAiProvider()
				.willCallTool("noop", "{}")
				.willSay("answer");

		new Agent(provider, AgentRole.DIAGNOSE_TEST, MODELS, List.of(new NoopTool())).ask("s", "c");

		assertEquals(1, provider.getToolInvocations().size());
	}

	@Test
	void testAnswerIsReturnedVerbatim() throws Exception {
		FakeAiProvider provider = new FakeAiProvider().willSay("  the diff  ");

		String answer = new Agent(provider, AgentRole.FIX_MINIMAL, MODELS, List.of())
				.ask("s", "c").text();

		assertEquals("  the diff  ", answer);
	}
}
