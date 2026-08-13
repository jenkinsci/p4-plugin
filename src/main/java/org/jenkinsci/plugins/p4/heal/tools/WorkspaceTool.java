package org.jenkinsci.plugins.p4.heal.tools;

import hudson.FilePath;
import kong.unirest.core.json.JSONException;
import kong.unirest.core.json.JSONObject;
import org.jenkinsci.plugins.p4.heal.WorkspacePaths;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;

import java.io.IOException;

/**
 * Base for the read-only tools the model explores the workspace with.
 *
 * <p>Every path the model supplies is untrusted input. Paths are confined to the
 * build workspace here, once, rather than in each tool — an absolute path, a
 * drive letter or any {@code ..} segment is refused before it reaches the
 * filesystem.
 */
public abstract class WorkspaceTool implements AiTool {

	/**
	 * Tool output is fed straight back into a prompt, so every tool caps what it
	 * returns rather than letting one call swamp the context.
	 */
	protected static final int MAX_OUTPUT_CHARS = 40000;

	protected final FilePath workspace;

	protected WorkspaceTool(FilePath workspace) {
		this.workspace = workspace;
	}

	/**
	 * Resolve a model-supplied path against the workspace.
	 *
	 * @param relative the path the model asked for
	 * @return the resolved location inside the workspace
	 * @throws IOException if the path is missing, absolute, or escapes the workspace
	 */
	protected FilePath resolve(String relative) throws IOException {
		if (relative == null || relative.isBlank()) {
			throw new IOException("A 'path' is required.");
		}

		try {
			return new FilePath(workspace, WorkspacePaths.confine(relative));
		} catch (IllegalArgumentException e) {
			throw new IOException("Path rejected: " + e.getMessage(), e);
		}
	}

	/**
	 * @param jsonInput tool arguments as JSON
	 * @return the parsed object
	 * @throws IOException if the model sent something that is not a JSON object
	 */
	protected static JSONObject arguments(String jsonInput) throws IOException {
		try {
			return new JSONObject(jsonInput == null || jsonInput.isBlank() ? "{}" : jsonInput);
		} catch (JSONException e) {
			throw new IOException("Tool arguments were not valid JSON: " + e.getMessage(), e);
		}
	}

	/**
	 * @param workspacePath a path inside the workspace
	 * @return it, relative to the workspace root, with forward slashes
	 */
	protected String relativise(FilePath workspacePath) {
		return WorkspacePaths.relativise(workspace.getRemote(), workspacePath.getRemote());
	}

	/**
	 * @param text tool output
	 * @return the output, capped
	 */
	protected static String capped(String text) {
		if (text.length() <= MAX_OUTPUT_CHARS) {
			return text;
		}
		return text.substring(0, MAX_OUTPUT_CHARS) + "\n[...truncated...]";
	}
}
