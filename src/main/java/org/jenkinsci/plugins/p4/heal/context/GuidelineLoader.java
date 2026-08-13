package org.jenkinsci.plugins.p4.heal.context;

import hudson.FilePath;
import org.jenkinsci.plugins.p4.heal.WorkspacePaths;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects the repository's own AI configuration — {@code CLAUDE.md},
 * {@code .claude/guidelines/**}, and friends — from the build workspace so a
 * generated fix follows the same conventions a human contributor would.
 *
 * <p>Output ordering is sorted and therefore stable between calls. That matters
 * for more than tidiness: this text sits in the cached prefix of every request in
 * a heal run, and any reordering would change the prefix bytes and throw away the
 * prompt cache.
 */
public final class GuidelineLoader implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * The AI configuration files commonly found at the root of a repository.
	 */
	public static final List<String> DEFAULT_GLOBS = List.of(
			"CLAUDE.md",
			"AGENTS.md",
			".cursorrules",
			// Ant's '**' matches zero directories too, so this covers the guidelines
			// directory itself as well as anything nested under it.
			".claude/guidelines/**/*.md",
			".github/copilot-instructions.md");

	private static final String TRUNCATION_NOTE =
			"\n[Guidelines truncated: the remaining files did not fit the configured size limit.]\n";

	private final List<String> globs;
	private final int maxBytes;

	/**
	 * @param globs    Ant-style include patterns, relative to the workspace root
	 * @param maxBytes ceiling on the assembled text, so an oversized docs tree
	 *                 cannot crowd out the failure context in the prompt
	 */
	public GuidelineLoader(List<String> globs, int maxBytes) {
		this.globs = List.copyOf(globs);
		this.maxBytes = maxBytes;
	}

	/**
	 * Read every matching file under the workspace.
	 *
	 * @param workspace the build workspace, local or on an agent
	 * @return the guidelines as one labelled block, or an empty string if the
	 *         repository carries none
	 * @throws IOException          if the workspace cannot be read
	 * @throws InterruptedException if the remote call is interrupted
	 */
	public String load(FilePath workspace) throws IOException, InterruptedException {
		if (globs.isEmpty()) {
			return "";
		}

		// TreeMap both de-duplicates files matched by more than one glob and fixes
		// the ordering, which the prompt cache depends on.
		TreeMap<String, FilePath> matched = new TreeMap<>();
		String root = workspace.getRemote();
		for (FilePath found : workspace.list(String.join(",", globs))) {
			matched.put(WorkspacePaths.relativise(root, found.getRemote()), found);
		}

		StringBuilder text = new StringBuilder();
		boolean truncated = false;
		int used = 0;

		for (Map.Entry<String, FilePath> entry : matched.entrySet()) {
			String heading = "### " + entry.getKey() + "\n";

			// Measured from the file's own size before it is read: an oversized docs
			// tree on an agent should not be pulled across the channel only to be
			// thrown away. A byte count over-states a UTF-8 character count at worst,
			// so this never lets more through than the cap allows.
			long size = heading.length() + entry.getValue().length() + 2;
			if (used + size > maxBytes) {
				truncated = true;
				continue;
			}

			text.append(heading).append(entry.getValue().readToString()).append("\n\n");
			used += (int) size;
		}

		if (truncated) {
			text.append(TRUNCATION_NOTE);
		}
		return text.toString();
	}
}
