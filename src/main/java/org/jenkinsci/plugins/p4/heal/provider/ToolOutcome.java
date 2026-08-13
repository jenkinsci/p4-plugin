package org.jenkinsci.plugins.p4.heal.provider;

/**
 * The result of running one tool, on its way back to the model.
 *
 * @param toolUseId the id from the corresponding {@link ToolUse}
 * @param content   what the tool produced, or the error text if it failed
 * @param error     true if the tool failed; a failure is reported to the model so
 *                  it can adapt, rather than aborting the conversation
 */
public record ToolOutcome(String toolUseId, String content, boolean error) {
}
