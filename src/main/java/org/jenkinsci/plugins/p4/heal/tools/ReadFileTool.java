package org.jenkinsci.plugins.p4.heal.tools;

import hudson.FilePath;
import kong.unirest.core.json.JSONObject;

import java.io.IOException;

/**
 * Lets the model read a file from the build workspace.
 *
 * <p>Output is line-numbered so the model can cite a location precisely, which
 * matters when the next thing it produces is a unified diff.
 */
public class ReadFileTool extends WorkspaceTool {

	public ReadFileTool(FilePath workspace) {
		super(workspace);
	}

	@Override
	public String getName() {
		return "read_file";
	}

	@Override
	public String getDescription() {
		return "Read a file from the build workspace. Returns the contents with line numbers. "
				+ "Use this to see the code around a failure before proposing a change, and to "
				+ "check how a symbol is actually defined rather than assuming. Optionally pass "
				+ "startLine and endLine (1-based, inclusive) to read part of a large file.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\","
				+ "\"properties\":{"
				+ "\"path\":{\"type\":\"string\",\"description\":"
				+ "\"Workspace-relative path, e.g. src/main/java/Foo.java\"},"
				+ "\"startLine\":{\"type\":\"integer\",\"description\":\"First line to return\"},"
				+ "\"endLine\":{\"type\":\"integer\",\"description\":\"Last line to return\"}},"
				+ "\"required\":[\"path\"]}";
	}

	@Override
	public String execute(String jsonInput) throws IOException {
		JSONObject arguments = arguments(jsonInput);
		FilePath file = resolve(arguments.optString("path", ""));

		try {
			if (!file.exists() || file.isDirectory()) {
				throw new IOException("No such file in the workspace: "
						+ arguments.optString("path", ""));
			}

			String[] lines = file.readToString().split("\r?\n", -1);
			int from = Math.max(1, arguments.optInt("startLine", 1));
			int to = Math.min(lines.length, arguments.optInt("endLine", lines.length));

			StringBuilder numbered = new StringBuilder();
			for (int i = from; i <= to; i++) {
				numbered.append(i).append(": ").append(lines[i - 1]).append('\n');
			}
			return capped(numbered.toString());

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while reading " + arguments.optString("path", ""), e);
		}
	}
}
