package org.jenkinsci.plugins.p4.console;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;

public class P4ConsoleAnnotator extends ConsoleAnnotator<Object> {

	private static final long serialVersionUID = 1L;
	private int id = 0;
	private int depth = 0;

	private static String COMMAND = "(p4):cmd:";
	private static String STOP = "(p4):stop:";

	@Override
	public ConsoleAnnotator<Object> annotate(Object context, MarkupText text) {

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

		String head = "<div class=\"titleDiv\">";
		text.addMarkup(COMMAND.length(), head);

		StringBuffer sb = new StringBuffer();

		sb.append("<script>\n");
		sb.append("function toggle(showHideDiv, switchTextDiv) {\n");
		sb.append("\tvar tog = document.getElementById(showHideDiv);\n");
		sb.append("\tvar text = document.getElementById(switchTextDiv);\n");
		sb.append("\tif(tog.style.display == \"block\") {\n");
		sb.append("    \ttog.style.display = \"none\";\n");
		sb.append("\t\ttext.innerHTML = \"+\";\n");
		sb.append("  \t}\n");
		sb.append("\telse {\n");
		sb.append("\t\ttog.style.display = \"block\";\n");
		sb.append("\t\ttext.innerHTML = \"-\";\n");
		sb.append("\t}\n");
		sb.append("}\n");
		sb.append("</script>");

		sb.append(" <a class=\"linkDiv\" id=\"");
		sb.append("p4title" + id);
		sb.append("\" href=\"javascript:toggle('");
		sb.append("p4content" + id);
		sb.append("','");
		sb.append("p4title" + id);
		sb.append("');\">");
		sb.append("+");
		sb.append("</a>");
		sb.append("</div>");

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
			StringBuffer sb = new StringBuffer();
			sb.append("</div></div>");
			text.addMarkup(text.length(), sb.toString());
			depth--;
		}
	}

}
