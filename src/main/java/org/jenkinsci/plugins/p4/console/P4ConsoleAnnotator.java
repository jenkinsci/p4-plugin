package org.jenkinsci.plugins.p4.console;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;

import java.io.Serial;

public class P4ConsoleAnnotator extends ConsoleAnnotator<Object> {

	@Serial
	private static final long serialVersionUID = 1L;
	private int depth = 0;

	public static final String COMMAND = "(p4):cmd:";
	public static final String STOP = "(p4):stop:";

	@Override
	public ConsoleAnnotator<Object> annotate(@NonNull Object context, @NonNull MarkupText text) {

		String line = text.getText();
		if (line.startsWith(COMMAND)) {
			push(text);
		}

		if (line.startsWith(STOP)) {
			pop(text);
		}

		return this;
	}

	private void push(MarkupText text) {
		text.hide(0, COMMAND.length());
		text.addMarkup(COMMAND.length(), "<details><summary class=\"titleDiv\">");
		text.addMarkup(text.length() - 1, "</summary><div class=\"contentDiv\">");
		text.hide(text.length() - 1, text.length());
		depth++;
	}

	private void pop(MarkupText text) {
		text.hide(0, text.length());

		if (depth > 0) {
			text.addMarkup(text.length(), "</div></details>");
			depth--;
		}
	}

}
