package org.jenkinsci.plugins.p4.heal.tools;

import hudson.FilePath;
import kong.unirest.core.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lets the model list workspace paths by pattern, so it can orient itself in a
 * repository it has never seen before reading anything.
 */
public class GlobTool extends WorkspaceTool {

	private static final int MAX_PATHS = 500;

	public GlobTool(FilePath workspace) {
		super(workspace);
	}

	@Override
	public String getName() {
		return "glob";
	}

	@Override
	public String getDescription() {
		return "List files in the build workspace matching an Ant-style glob, one path per "
				+ "line, sorted. Use this to find where something lives before reading it, for "
				+ "example the test that corresponds to a class, or every implementation of an "
				+ "interface.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\","
				+ "\"properties\":{"
				+ "\"glob\":{\"type\":\"string\",\"description\":"
				+ "\"Ant-style include pattern, e.g. src/test/java/**/*Test.java\"}},"
				+ "\"required\":[\"glob\"]}";
	}

	@Override
	public String execute(String jsonInput) throws IOException {
		JSONObject arguments = arguments(jsonInput);

		String glob = arguments.optString("glob", "");
		if (glob.isBlank()) {
			throw new IOException("A 'glob' is required.");
		}

		try {
			List<String> paths = new ArrayList<>();
			for (FilePath found : workspace.list(glob)) {
				paths.add(relativise(found));
			}

			if (paths.isEmpty()) {
				return "No matches for " + glob;
			}

			Collections.sort(paths);
			StringBuilder listing = new StringBuilder();
			int shown = 0;
			for (String path : paths) {
				if (++shown > MAX_PATHS) {
					listing.append("[...").append(paths.size() - MAX_PATHS)
							.append(" more; narrow the glob]\n");
					break;
				}
				listing.append(path).append('\n');
			}
			return capped(listing.toString());

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while listing the workspace", e);
		}
	}
}
