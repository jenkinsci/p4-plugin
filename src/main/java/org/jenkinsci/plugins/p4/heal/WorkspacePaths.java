package org.jenkinsci.plugins.p4.heal;

import java.util.regex.Pattern;

/**
 * The one place that decides whether a path stays inside the build workspace.
 *
 * <p>Both the patch parser and the model's read-only tools take paths straight
 * from model output, which is untrusted. The rule lives here so that hardening it
 * hardens every caller, rather than one copy of it.
 */
public final class WorkspacePaths {

	private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");

	private WorkspacePaths() {
	}

	/**
	 * Normalise separators and refuse anything that could leave the workspace.
	 *
	 * <p>Reported as an unchecked exception because callers already have an
	 * exception type of their own — a patch fault and a bad tool argument are told
	 * apart by the caller, not here.
	 *
	 * @param raw an untrusted path
	 * @return the path, trimmed, with forward slashes
	 * @throws IllegalArgumentException if it is empty, absolute, or holds a
	 *                                  {@code ..} segment
	 */
	public static String confine(String raw) {
		String path = raw == null ? "" : raw.replace('\\', '/').trim();

		if (path.isEmpty()) {
			throw new IllegalArgumentException("the path is empty");
		}
		if (path.startsWith("/") || DRIVE_LETTER.matcher(path).matches()) {
			throw new IllegalArgumentException(
					"'" + path + "' is absolute; workspace-relative paths only");
		}
		// Checked by segment boundary rather than by splitting, so a name that merely
		// starts with dots ("..b") is still allowed.
		if ("..".equals(path) || path.startsWith("../") || path.contains("/../")
				|| path.endsWith("/..")) {
			throw new IllegalArgumentException("'" + path + "' escapes the workspace");
		}
		return path;
	}

	/**
	 * @param root     the workspace root, as a remote path
	 * @param absolute a path inside that root
	 * @return the path relative to the root, with forward slashes
	 */
	public static String relativise(String root, String absolute) {
		String path = absolute;
		if (path.startsWith(root)) {
			path = path.substring(root.length());
		}
		path = path.replace('\\', '/');
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		return path;
	}
}
