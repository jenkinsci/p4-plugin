package org.jenkinsci.plugins.p4.heal.provider;

import java.io.IOException;

/**
 * A capability the model may invoke during a conversation.
 *
 * <p>Every tool the plugin offers is read-only. The model can look at the
 * repository — read a file, grep, list paths, inspect Perforce history — but it
 * cannot write, run a shell, or change Perforce state. Applying a patch,
 * compiling, testing and shelving are done by plugin code after the model has
 * finished talking, so the anti-cheat scan and the verify ladder cannot be
 * circumvented by the thing they are checking.
 *
 * <p>Input and output cross this boundary as JSON strings rather than mapped
 * objects, so no JSON library leaks into the provider extension point.
 */
public interface AiTool {

	/**
	 * @return the tool name the model calls, e.g. {@code read_file}
	 */
	String getName();

	/**
	 * @return what the tool does and when to reach for it; this is the model's
	 *         only guide to using it correctly
	 */
	String getDescription();

	/**
	 * @return a JSON Schema object describing the tool's parameters
	 */
	String getInputSchema();

	/**
	 * Run the tool.
	 *
	 * @param jsonInput arguments as a JSON object, matching {@link #getInputSchema()}
	 * @return the result to hand back to the model, as plain text
	 * @throws IOException if the tool cannot complete; the provider is expected to
	 *                     report this to the model as a tool error rather than
	 *                     aborting the conversation
	 */
	String execute(String jsonInput) throws IOException;
}
