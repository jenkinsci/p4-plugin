package org.jenkinsci.plugins.p4.heal.patch;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * One {@code @@ -a,b +c,d @@} block of a unified diff.
 *
 * <p>Body lines are kept verbatim, including the leading {@code ' '}, {@code '+'}
 * or {@code '-'} marker, so a hunk can be applied without re-deriving them.
 */
public final class Hunk implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final int oldStart;
	private final List<String> lines;

	/**
	 * The header's line counts are used by the parser to consume exactly this hunk's
	 * body and are not kept: applying a hunk needs where it starts in the original
	 * file and the body lines, and the body already says how many of each there are.
	 */
	Hunk(int oldStart, List<String> lines) {
		this.oldStart = oldStart;
		this.lines = List.copyOf(lines);
	}

	public int getOldStart() {
		return oldStart;
	}

	/**
	 * Body lines with their leading marker character intact.
	 *
	 * @return unmodifiable list of raw hunk lines
	 */
	public List<String> getLines() {
		return Collections.unmodifiableList(lines);
	}
}
