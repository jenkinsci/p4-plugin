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
}
