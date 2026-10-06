package org.jenkinsci.plugins.p4.unit.console;

import hudson.MarkupText;
import org.jenkinsci.plugins.p4.console.P4ConsoleAnnotator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P4ConsoleAnnotatorTest {

	@Test
	void testAnnotateCommandLineAddsCollapsibleMarkup() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		MarkupText text = new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 sync");

		Object result = annotator.annotate(new Object(), text);

		assertSame(annotator, result);
		String html = text.toString(true);
		assertTrue(html.contains("<details"));
		assertTrue(html.contains("<summary class=\"titleDiv\">"));
		assertTrue(html.contains("contentDiv"));
	}

	@Test
	void testAnnotateCommandLineMarkupNeedsNoScript() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		MarkupText text = new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 sync");

		annotator.annotate(new Object(), text);

		String html = text.toString(true);
		assertFalse(html.contains("javascript:"));
		assertFalse(html.contains("toggle("));
		assertFalse(html.contains("onclick"));
	}

	@Test
	void testAnnotateStopLineWithOpenDepthClosesDetails() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		annotator.annotate(new Object(), new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 sync"));

		MarkupText stop = new MarkupText(P4ConsoleAnnotator.STOP + "1");
		annotator.annotate(new Object(), stop);

		assertTrue(stop.toString(true).contains("</div></details>"));
	}

	@Test
	void testAnnotateStopLineWithoutOpenDepthDoesNotCloseDetails() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();

		MarkupText stop = new MarkupText(P4ConsoleAnnotator.STOP + "1");
		Object result = annotator.annotate(new Object(), stop);

		assertSame(annotator, result);
		assertFalse(stop.toString(true).contains("</details>"));
	}

	@Test
	void testAnnotateUnrelatedLineIsUnchanged() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		MarkupText text = new MarkupText("just some output");

		Object result = annotator.annotate(new Object(), text);

		assertSame(annotator, result);
		assertEquals("just some output", text.toString(true));
	}

	@Test
	void testAnnotateNewlineTerminatedCommandLineKeepsFullCommandVisible() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		MarkupText text = new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 sync\n");

		annotator.annotate(new Object(), text);

		assertTrue(text.toString(true).contains(
				"<details><summary class=\"titleDiv\">p4 sync</summary><div class=\"contentDiv\">"));
	}

	@Test
	void testAnnotateNestedCommandStopPairsAreBalanced() {
		P4ConsoleAnnotator annotator = new P4ConsoleAnnotator();
		MarkupText outer = new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 sync\n");
		MarkupText inner = new MarkupText(P4ConsoleAnnotator.COMMAND + "p4 fstat\n");
		MarkupText innerStop = new MarkupText(P4ConsoleAnnotator.STOP + "2\n");
		MarkupText outerStop = new MarkupText(P4ConsoleAnnotator.STOP + "1\n");

		annotator.annotate(new Object(), outer);
		annotator.annotate(new Object(), inner);
		annotator.annotate(new Object(), innerStop);
		annotator.annotate(new Object(), outerStop);

		String html = outer.toString(true) + inner.toString(true) + innerStop.toString(true) + outerStop.toString(true);
		assertEquals(2, countOccurrences(html, "<details>"));
		assertEquals(2, countOccurrences(html, "</details>"));
		assertEquals(2, countOccurrences(html, "<summary "));
		assertEquals(2, countOccurrences(html, "</summary>"));
	}

	private static int countOccurrences(String html, String token) {
		int count = 0;
		for (int i = html.indexOf(token); i >= 0; i = html.indexOf(token, i + token.length())) {
			count++;
		}
		return count;
	}
}
