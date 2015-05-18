package org.jenkinsci.plugins.p4.console;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;

public class P4ConsoleAnnotator extends ConsoleAnnotator<Object> {

	private static final long serialVersionUID = 1L;
	private int id = 0;

	@Override
	public ConsoleAnnotator<Object> annotate(Object context, MarkupText text) {

		String line = text.getText();
		if (line.startsWith("[p4log:start")) {
			push(text);
		}

		if (line.startsWith("[p4log:stop")) {
			pop(text);
		}

		return this;
	}

	private void push(MarkupText text) {
		text.hide(0, text.length());
		
		StringBuffer sb = new StringBuffer();
		sb.append("<div class=\"headerDiv\">");
		sb.append("... ");
		sb.append("<a id=\"");
		sb.append("p4title" + id);
		sb.append("\" href=\"javascript:toggle('");
		sb.append("p4content" + id);
		sb.append("','");
		sb.append("p4title" + id);
		sb.append("');\">");
		sb.append("expand");
		sb.append("</a>");
		
		sb.append("</div>");
		sb.append("<div style=\"clear:both;\">");
		sb.append("</div>");
		sb.append("<div class=\"contentDiv\">");
		sb.append("<div id=\"");
		sb.append("p4content" + id);
		sb.append("\" style=\"display: none;\">");
		text.addMarkup(text.length(), sb.toString());
		
		id++;
	}

	private void pop(MarkupText text) {
		text.hide(0, text.length());
		
		StringBuffer sb = new StringBuffer();
		sb.append("</div></div>");
		text.addMarkup(text.length(), sb.toString());
	}

}
