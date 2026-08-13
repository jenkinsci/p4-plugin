package org.jenkinsci.plugins.p4.heal.provider;

/**
 * A tool the model asked to run.
 *
 * @param id    the id the result must be returned against
 * @param name  the tool name
 * @param input arguments as a JSON object string
 */
public record ToolUse(String id, String name, String input) {
}
