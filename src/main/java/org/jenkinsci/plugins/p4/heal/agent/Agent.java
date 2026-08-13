package org.jenkinsci.plugins.p4.heal.agent;

import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.jenkinsci.plugins.p4.heal.provider.AiRequest;
import org.jenkinsci.plugins.p4.heal.provider.AiResult;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;

import java.io.IOException;
import java.util.List;

/**
 * One agent: a role, a model chosen to suit it, and the read-only tools it may
 * use to look at the repository.
 *
 * <p>The role prompt goes in the user turn, not the system prompt. The system
 * prompt holds the repository guidelines and failure context, which are identical
 * for every agent in a heal run and therefore sit in the cached request prefix;
 * putting the per-role text there would give each agent a different prefix and
 * throw the cache away.
 */
public final class Agent {

	private static final String SEPARATOR = "\n\n---\n\n";

	private final AiProvider provider;
	private final AgentRole role;
	private final HealModels models;
	private final List<AiTool> tools;

	/**
	 * @param provider the model to talk to
	 * @param role     what this agent is for
	 * @param models   model routing for the run
	 * @param tools    read-only tools this agent may use
	 */
	public Agent(AiProvider provider, AgentRole role, HealModels models, List<AiTool> tools) {
		this.provider = provider;
		this.role = role;
		this.models = models;
		this.tools = List.copyOf(tools);
	}

	/**
	 * Ask this agent to do its job.
	 *
	 * @param system  the shared, cacheable prefix: guidelines plus failure context
	 * @param context what this particular agent needs to see — a diagnosis, a
	 *                candidate patch, feedback from a previous attempt
	 * @return the model's answer
	 * @throws IOException if the provider could not be reached
	 */
	public AiResult ask(String system, String context) throws IOException {
		AiRequest request = new AiRequest(
				system,
				role.prompt() + SEPARATOR + context,
				role.isCritic() ? models.cheapModel() : models.smartModel(),
				models.maxTokens(),
				role.isCritic() ? models.cheapEffort() : models.smartEffort());

		return provider.converse(request, tools);
	}
}
