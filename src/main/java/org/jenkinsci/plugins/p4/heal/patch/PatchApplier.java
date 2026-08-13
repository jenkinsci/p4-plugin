package org.jenkinsci.plugins.p4.heal.patch;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a parsed diff to file content.
 *
 * <p>Rung 2 of the verify ladder: exact application only. Every context and
 * removed line must match the file byte for byte at the position the hunk
 * declares. There is no fuzz factor and no search for a nearby match.
 *
 * <p>That strictness is deliberate. Fuzzy patching exists to help a human apply
 * a stale diff by hand; here, a hunk that does not match means the model was
 * working from an idea of the file rather than the file, and applying it
 * anywhere is a guess. Failing loudly costs one retry, which is cheap.
 */
public final class PatchApplier {

	private PatchApplier() {
	}

	/**
	 * Apply one file's worth of hunks.
	 *
	 * @param original the current file content
	 * @param diff     the changes to make to it
	 * @return the patched content
	 * @throws PatchFormatException if any hunk does not match exactly
	 */
	public static String apply(String original, FileDiff diff) throws PatchFormatException {
		String[] lines = original.split("\n", -1);
		List<String> patched = new ArrayList<>();

		int cursor = 0;
		for (Hunk hunk : diff.getHunks()) {
			int start = hunk.getOldStart() - 1;

			if (start < cursor) {
				throw new PatchFormatException("Hunks overlap in '" + diff.getPath()
						+ "' at line " + hunk.getOldStart() + "; a patch must describe each"
						+ " line at most once.");
			}
			if (start > lines.length) {
				throw new PatchFormatException("Hunk at line " + hunk.getOldStart() + " is past"
						+ " the end of '" + diff.getPath() + "', which has "
						+ lines.length + " lines.");
			}

			while (cursor < start) {
				patched.add(lines[cursor++]);
			}

			for (String line : hunk.getLines()) {
				char marker = line.isEmpty() ? ' ' : line.charAt(0);
				String content = line.isEmpty() ? "" : line.substring(1);

				switch (marker) {
					case ' ' -> {
						expect(lines, cursor, content, diff.getPath(), "context");
						patched.add(lines[cursor++]);
					}
					case '-' -> {
						expect(lines, cursor, content, diff.getPath(), "removed");
						cursor++;
					}
					case '+' -> patched.add(content);
					default -> throw new PatchFormatException(
							"Unexpected line in hunk for '" + diff.getPath() + "': " + line);
				}
			}
		}

		while (cursor < lines.length) {
			patched.add(lines[cursor++]);
		}
		return String.join("\n", patched);
	}

	private static void expect(String[] lines, int cursor, String content, String path, String kind)
			throws PatchFormatException {

		if (cursor >= lines.length) {
			throw new PatchFormatException("Patch for '" + path + "' runs past the end of the"
					+ " file; expected a " + kind + " line but the file has only "
					+ lines.length + " lines.");
		}
		if (!lines[cursor].equals(content)) {
			throw new PatchFormatException("Patch does not match '" + path + "' at line "
					+ (cursor + 1) + ".\n  file:  " + lines[cursor] + "\n  patch: " + content);
		}
	}
}
