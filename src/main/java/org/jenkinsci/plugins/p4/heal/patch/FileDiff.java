package org.jenkinsci.plugins.p4.heal.patch;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The changes a unified diff makes to a single file.
 */
public final class FileDiff implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String path;
	private final List<Hunk> hunks;

	FileDiff(String path, List<Hunk> hunks) {
		this.path = path;
		this.hunks = List.copyOf(hunks);
	}

	/**
	 * Workspace-relative target path, with any {@code a/} or {@code b/} prefix
	 * stripped and separators normalised to {@code /}.
	 *
	 * @return relative path of the file this diff modifies
	 */
	public String getPath() {
		return path;
	}

	public List<Hunk> getHunks() {
		return Collections.unmodifiableList(hunks);
	}

	/**
	 * Lines this diff introduces, without the leading {@code '+'} marker.
	 *
	 * @return added lines across every hunk, in order
	 */
	public List<String> getAddedLines() {
		return markedLines('+');
	}

	/**
	 * Lines this diff deletes, without the leading {@code '-'} marker.
	 *
	 * @return removed lines across every hunk, in order
	 */
	public List<String> getRemovedLines() {
		return markedLines('-');
	}

	private List<String> markedLines(char marker) {
		List<String> found = new ArrayList<>();
		for (Hunk hunk : hunks) {
			for (String line : hunk.getLines()) {
				if (!line.isEmpty() && line.charAt(0) == marker) {
					found.add(line.substring(1));
				}
			}
		}
		return found;
	}
}
