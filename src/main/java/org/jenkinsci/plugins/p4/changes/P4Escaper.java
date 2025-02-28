package org.jenkinsci.plugins.p4.changes;

import org.apache.commons.text.translate.NumericEntityEscaper;

import java.io.IOException;
import java.io.Writer;

public class P4Escaper extends NumericEntityEscaper {

	private final int below;
	private final int above;
	private final boolean between;

	/**
	 * <p>Constructs a <code>P4Escaper</code> for the specified range. This is
	 * the underlying method for the other constructors/builders. The <code>below</code>
	 * and <code>above</code> boundaries are inclusive when <code>between</code> is
	 * <code>true</code> and exclusive when it is <code>false</code>. </p>
	 *
	 * @param below   int value representing the lowest codepoint boundary
	 * @param above   int value representing the highest codepoint boundary
	 * @param between whether to escape between the boundaries or outside them
	 */
	private P4Escaper(final int below, final int above, final boolean between) {
		this.below = below;
		this.above = above;
		this.between = between;
	}

	/**
	 * <p>Constructs a <code>P4Escaper</code> below 0x20. </p>
	 *
	 * @return the newly created {@code P4Escaper} instance
	 */
	public static P4Escaper filter() {
		return new P4Escaper(0x20, Integer.MAX_VALUE, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean translate(final int codepoint, final Writer out) throws IOException {
		if (between) {
			if (codepoint < below || codepoint > above) {
				return false;
			}
		} else {
			if (codepoint >= below && codepoint <= above) {
				return false;
			}
		}

		// skip translation for \t \n \r
		if(codepoint == 0x09 || codepoint == 0x0A || codepoint == 0x0D) {
			return false;
		}

		out.write('?');
		return true;
	}
}
