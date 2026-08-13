package org.jenkinsci.plugins.p4.heal.context;

import hudson.console.ConsoleNote;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Everything known about a build failure, rendered as the prompt block every
 * agent in a heal run is given.
 *
 * <p>This text leaves the Jenkins controller for a third-party API, so it is
 * redacted on the way out. Build logs routinely carry credentials that Jenkins
 * itself never masked — a Perforce ticket echoed by a command, a token in a
 * curl line, a password in a stack trace — and none of that should be shipped
 * off-box.
 */
public final class FailureContext implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final String MASK = "***";

	/**
	 * {@code key = value} pairs whose key names a credential.
	 */
	private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
			"(?i)(password|passwd|pwd|secret|token|api[_-]?key)(\\s*[=:]\\s*)(\\S+)");

	private static final Pattern AUTH_HEADER = Pattern.compile(
			"(?i)(authorization\\s*:\\s*)(\\S+)(\\s+\\S+)?");

	/**
	 * Vendor-prefixed API keys, e.g. {@code sk-ant-...}.
	 */
	private static final Pattern VENDOR_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{16,}");

	/**
	 * A Perforce ticket is 32 uppercase hex characters. Restricting the pattern to
	 * uppercase keeps it off ordinary lowercase digests in build output.
	 */
	private static final Pattern P4_TICKET = Pattern.compile("\\b[0-9A-F]{32}\\b");

	private final String logExcerpt;

	/**
	 * Failing test id to its failure message. One map rather than a set plus a
	 * parallel map: the ids are exactly the keys, so two collections could only
	 * disagree. A {@link TreeMap} fixes the iteration order the prompt cache needs.
	 */
	private final Map<String, String> failures;

	private final List<String> changedFiles;

	/**
	 * @param logExcerpt   build log tail, already excerpted
	 * @param failures     per-test failure message, keyed by test id
	 * @param changedFiles files in the change that broke the build
	 */
	public FailureContext(String logExcerpt, Map<String, String> failures,
	                      List<String> changedFiles) {
		this.logExcerpt = logExcerpt == null ? "" : logExcerpt;
		this.failures = new TreeMap<>(failures);
		this.changedFiles = List.copyOf(changedFiles);
	}

	public Set<String> getFailingTests() {
		return Collections.unmodifiableSet(failures.keySet());
	}

	public List<String> getChangedFiles() {
		return Collections.unmodifiableList(changedFiles);
	}

	/**
	 * Strip Jenkins console annotations from a build log tail.
	 *
	 * <p>Console notes are an encoded binary payload; left in place they would be
	 * unreadable noise in the prompt. The caller bounds how many lines it asks the
	 * build for, so nothing is trimmed here.
	 *
	 * @param lines trailing console lines, as {@code Run.getLog(int)} returns them
	 * @return the excerpt, never null
	 */
	public static String excerpt(List<String> lines) {
		if (lines == null || lines.isEmpty()) {
			return "";
		}
		return ConsoleNote.removeNotes(String.join("\n", lines));
	}

	/**
	 * Mask credentials that commonly leak into build output.
	 *
	 * <p>The patterns are deliberately broad. Over-masking costs the model a
	 * little context; under-masking sends a live credential to a third party.
	 *
	 * @param text any text bound for the model
	 * @return the text with recognised secrets replaced
	 */
	public static String redact(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		String redacted = SECRET_ASSIGNMENT.matcher(text).replaceAll("$1$2" + MASK);
		redacted = AUTH_HEADER.matcher(redacted).replaceAll("$1" + MASK);
		redacted = VENDOR_KEY.matcher(redacted).replaceAll(MASK);
		redacted = P4_TICKET.matcher(redacted).replaceAll(MASK);
		return redacted;
	}

	/**
	 * Render the failure as the prompt block shared by every agent.
	 *
	 * <p>Ordering is sorted so repeated runs produce byte-identical text; this
	 * block sits in the cached request prefix.
	 *
	 * @return the rendered, redacted context
	 */
	public String toPromptBlock() {
		List<String> sections = new ArrayList<>();

		if (!failures.isEmpty()) {
			StringBuilder tests = new StringBuilder("## Failing tests\n");
			for (Map.Entry<String, String> failure : failures.entrySet()) {
				tests.append("- ").append(failure.getKey()).append('\n');
				String detail = failure.getValue();
				if (detail != null && !detail.isBlank()) {
					tests.append("  ").append(redact(detail).replace("\n", "\n  ")).append('\n');
				}
			}
			sections.add(tests.toString());
		}

		if (!changedFiles.isEmpty()) {
			StringBuilder files = new StringBuilder("## Files in the change that broke the build\n");
			for (String file : new TreeSet<>(changedFiles)) {
				files.append("- ").append(file).append('\n');
			}
			sections.add(files.toString());
		}

		if (!logExcerpt.isBlank()) {
			sections.add("## Build log (tail)\n```\n" + redact(logExcerpt) + "\n```\n");
		}

		return String.join("\n", sections);
	}
}
