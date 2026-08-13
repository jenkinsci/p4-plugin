package org.jenkinsci.plugins.p4.heal.tools;

import hudson.FilePath;
import kong.unirest.core.json.JSONObject;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Lets the model search the workspace by regular expression.
 *
 * <p>This is what makes the difference between a fix written from a stack trace
 * and one written with knowledge of the code: the model can find every caller of
 * a method before changing its signature.
 */
public class GrepTool extends WorkspaceTool {

	private static final int MAX_MATCHES = 200;
	private static final String DEFAULT_GLOB = "**/*";

	/**
	 * Files above this are skipped rather than pulled across the channel. The
	 * default glob is the whole workspace, which on a build agent includes compiled
	 * artefacts; no source file a fix needs to read is this big.
	 */
	private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;

	public GrepTool(FilePath workspace) {
		super(workspace);
	}

	@Override
	public String getName() {
		return "grep";
	}

	@Override
	public String getDescription() {
		return "Search the build workspace with a Java regular expression. Returns matching "
				+ "lines as path:line: text. Use this to find callers of a method before you "
				+ "change it, to locate where a symbol is defined, or to check whether a helper "
				+ "already exists. Narrow the search with an Ant-style glob when you can.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\","
				+ "\"properties\":{"
				+ "\"pattern\":{\"type\":\"string\",\"description\":\"Java regular expression\"},"
				+ "\"glob\":{\"type\":\"string\",\"description\":"
				+ "\"Ant-style include pattern limiting which files are searched,"
				+ " e.g. src/main/java/**\"}},"
				+ "\"required\":[\"pattern\"]}";
	}

	@Override
	public String execute(String jsonInput) throws IOException {
		JSONObject arguments = arguments(jsonInput);

		String expression = arguments.optString("pattern", "");
		if (expression.isBlank()) {
			throw new IOException("A 'pattern' is required.");
		}

		Pattern pattern;
		try {
			pattern = Pattern.compile(expression);
		} catch (PatternSyntaxException e) {
			throw new IOException("'" + expression + "' is not a valid regular expression: "
					+ e.getDescription(), e);
		}

		String glob = arguments.optString("glob", DEFAULT_GLOB);
		if (glob.isBlank()) {
			glob = DEFAULT_GLOB;
		}

		try {
			StringBuilder found = new StringBuilder();
			int matches = 0;

			// FilePath.list(glob) returns files only, so there is no directory to skip
			// — and asking would cost a channel round-trip per entry.
			for (FilePath file : workspace.list(glob)) {
				if (file.length() > MAX_FILE_BYTES) {
					continue;
				}
				String[] lines = file.readToString().split("\r?\n", -1);
				for (int i = 0; i < lines.length; i++) {
					if (!pattern.matcher(lines[i]).find()) {
						continue;
					}
					if (++matches > MAX_MATCHES) {
						found.append("[...more matches suppressed; narrow the pattern or glob]\n");
						return capped(found.toString());
					}
					found.append(relativise(file)).append(':').append(i + 1).append(": ")
							.append(lines[i]).append('\n');
				}
			}

			if (matches == 0) {
				return "No matches for /" + expression + "/ in " + glob;
			}
			return capped(found.toString());

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while searching the workspace", e);
		}
	}
}
