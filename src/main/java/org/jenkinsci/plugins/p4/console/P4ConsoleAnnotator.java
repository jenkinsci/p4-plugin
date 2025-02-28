package org.jenkinsci.plugins.p4.console;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;

import java.io.Serial;

public class P4ConsoleAnnotator extends ConsoleAnnotator<Object> {

	@Serial
	private static final long serialVersionUID = 1L;
	private int id = 0;
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

		String head = "<span class=\"titleDiv\">";
		text.addMarkup(COMMAND.length(), head);

		StringBuilder sb = new StringBuilder();
		sb.append(" <a class=\"linkDiv\" id=\"");
		sb.append("p4title" + id);
		sb.append("\" href=\"javascript:toggle('");
		sb.append("p4content" + id);
		sb.append("','");
		sb.append("p4title" + id);
		sb.append("');\">");
		sb.append("+");
		sb.append("</a>");
		sb.append("</span>");

		sb.append("<div class=\"contentDiv\">");
		sb.append("<div id=\"");
		sb.append("p4content" + id);
		sb.append("\" style=\"display: none;\">");
		text.addMarkup(text.length() - 1, sb.toString());

		text.hide(text.length() - 1, text.length());
		id++;
		depth++;
	}

	private void pop(MarkupText text) {
		text.hide(0, text.length());

		if (depth > 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("</div></div>");
			text.addMarkup(text.length(), sb.toString());
			depth--;
		}
	}

}
