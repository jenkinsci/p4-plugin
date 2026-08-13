package org.jenkinsci.plugins.p4.heal.patch;

import org.jenkinsci.plugins.p4.heal.WorkspacePaths;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed unified diff.
 *
 * <p>Parsing is deliberately strict: a model that answers with prose, a truncated
 * hunk, or a header whose line counts do not match its body is rejected rather
 * than guessed at. Hunk bodies are consumed by the counts declared in the header,
 * so a diff can only parse if it is internally consistent.
 *
 * <p>Structural path safety is enforced here — absolute paths, {@code ..}
 * segments and {@code /dev/null} targets are refused. Policy about which paths a
 * build may modify belongs to {@link PatchGuard}, not to the parser.
 */
public final class UnifiedDiff implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Pattern HUNK_HEADER =
			Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

	private static final String OLD_FILE_MARKER = "--- ";
	private static final String NEW_FILE_MARKER = "+++ ";

	private final List<FileDiff> files;

	private UnifiedDiff(List<FileDiff> files) {
		this.files = List.copyOf(files);
	}

	public List<FileDiff> getFiles() {
		return Collections.unmodifiableList(files);
	}

	/**
	 * Parse unified diff text.
	 *
	 * <p>Content before the first {@code ---} header (a {@code diff --git} line,
	 * an {@code index} line, or any other preamble) is ignored.
	 *
	 * @param text raw diff text
	 * @return the parsed diff, always with at least one file and one hunk
	 * @throws PatchFormatException if the text is not a well-formed unified diff
	 *                              or targets a path that cannot be applied
	 */
	public static UnifiedDiff parse(String text) throws PatchFormatException {
		if (text == null || text.isBlank()) {
			throw new PatchFormatException("Patch is empty.");
		}

		String[] lines = text.split("\r?\n", -1);
		List<FileDiff> files = new ArrayList<>();

		int i = 0;
		while (i < lines.length) {
			if (!lines[i].startsWith(OLD_FILE_MARKER)) {
				i++;
				continue;
			}

			if (i + 1 >= lines.length || !lines[i + 1].startsWith(NEW_FILE_MARKER)) {
				throw new PatchFormatException(
						"'" + lines[i] + "' is not followed by a '+++' header.");
			}

			String path = relativePath(lines[i + 1].substring(NEW_FILE_MARKER.length()));
			i += 2;

			List<Hunk> hunks = new ArrayList<>();
			while (i < lines.length && lines[i].startsWith("@@")) {
				HunkScan scan = scanHunk(lines, i, path);
				hunks.add(scan.hunk);
				i = scan.next;
			}

			if (hunks.isEmpty()) {
				throw new PatchFormatException("No hunks found for '" + path + "'.");
			}
			files.add(new FileDiff(path, hunks));
		}

		if (files.isEmpty()) {
			throw new PatchFormatException(
					"No unified diff found; expected a '--- ' / '+++ ' file header.");
		}
		return new UnifiedDiff(files);
	}

	/**
	 * Read one hunk, consuming exactly as many body lines as its header declares.
	 */
	private static HunkScan scanHunk(String[] lines, int start, String path) throws PatchFormatException {
		Matcher header = HUNK_HEADER.matcher(lines[start]);
		if (!header.matches()) {
			throw new PatchFormatException(
					"Malformed hunk header in '" + path + "': " + lines[start]);
		}

		// The new-side start line is not read: a hunk is applied by locating its
		// old-side context in the file being patched, so only group 1 is needed, and
		// the two counts say how much body to consume.
		int oldStart = Integer.parseInt(header.group(1));
		int oldCount = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
		int newCount = header.group(4) == null ? 1 : Integer.parseInt(header.group(4));

		List<String> body = new ArrayList<>();
		int oldRemaining = oldCount;
		int newRemaining = newCount;
		int i = start + 1;

		while (oldRemaining > 0 || newRemaining > 0) {
			if (i >= lines.length) {
				throw new PatchFormatException("Truncated hunk at " + lines[start]
						+ " in '" + path + "'; the header declares more lines than the body contains.");
			}

			String line = lines[i++];
			if (line.startsWith("\\")) {
				// "\ No newline at end of file" is a marker, not a body line.
				continue;
			}

			// A context line whose trailing whitespace was stripped arrives as "".
			char marker = line.isEmpty() ? ' ' : line.charAt(0);
			switch (marker) {
				case ' ' -> {
					oldRemaining--;
					newRemaining--;
				}
				case '-' -> oldRemaining--;
				case '+' -> newRemaining--;
				default -> throw new PatchFormatException("Unexpected line in hunk for '"
						+ path + "'; expected ' ', '+' or '-' but found: " + line);
			}

			if (oldRemaining < 0 || newRemaining < 0) {
				throw new PatchFormatException("Hunk body at " + lines[start] + " in '" + path
						+ "' contains more lines than its header declares.");
			}
			body.add(line);
		}

		return new HunkScan(new Hunk(oldStart, body), i);
	}

	/**
	 * Normalise a diff header path and refuse anything that could escape the
	 * build workspace.
	 */
	private static String relativePath(String raw) throws PatchFormatException {
		// Unified diff headers may carry a tab-separated timestamp.
		String path = raw.split("\t", 2)[0].trim().replace('\\', '/');

		if ("/dev/null".equals(path)) {
			throw new PatchFormatException("Patch deletes a whole file; refusing.");
		}
		if (path.startsWith("a/") || path.startsWith("b/")) {
			path = path.substring(2);
		}
		try {
			return WorkspacePaths.confine(path);
		} catch (IllegalArgumentException e) {
			throw new PatchFormatException("Patch target rejected: " + e.getMessage());
		}
	}

	/**
	 * A parsed hunk plus the index of the first line after it.
	 */
	private record HunkScan(Hunk hunk, int next) {
	}
}
