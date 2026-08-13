package org.jenkinsci.plugins.p4.heal.agent;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictTest {

	@Test
	void testReadsAnAcceptance() {
		Verdict verdict = Verdict.parse("VERDICT: ACCEPT\nThe fix addresses the null check.");

		assertTrue(verdict.accepted());
		assertTrue(verdict.reason().contains("null check"), verdict.reason());
	}

	@Test
	void testReadsARejection() {
		Verdict verdict = Verdict.parse("VERDICT: REJECT\nThis masks the symptom.");

		assertFalse(verdict.accepted());
		assertTrue(verdict.reason().contains("masks the symptom"), verdict.reason());
	}

	@Test
	void testToleratesPreambleAndFormatting() {
		assertTrue(Verdict.parse("Let me review this.\n\n**VERDICT: ACCEPT**\nLooks right.")
				.accepted());
		assertTrue(Verdict.parse("  verdict:accept  \nfine").accepted());
		assertFalse(Verdict.parse("Analysis follows...\nVERDICT : REJECT\nbad").accepted());
	}

	@Test
	void testRejectionWinsWhenBothAppear() {
		Verdict verdict = Verdict.parse(
				"I considered whether to say VERDICT: ACCEPT here.\nVERDICT: REJECT\nOn balance, no.");

		assertFalse(verdict.accepted(),
				"a critic that says both things has not approved the patch");
	}

	@Test
	void testUnparseableVerdictIsTreatedAsRejection() {
		assertFalse(Verdict.parse("This looks fine to me!").accepted(),
				"a critic whose verdict cannot be read must not count as approval");
		assertFalse(Verdict.parse("").accepted());
		assertFalse(Verdict.parse(null).accepted());
	}

	@Test
	void testUnparseableVerdictSaysWhy() {
		assertTrue(Verdict.parse("waffle").reason().toLowerCase(Locale.ROOT)
				.contains("no verdict"), Verdict.parse("waffle").reason());
	}

	@Test
	void testReasonFallsBackToTheWholeAnswerWhenNothingFollows() {
		Verdict verdict = Verdict.parse("VERDICT: ACCEPT");

		assertTrue(verdict.accepted());
		assertEquals("", verdict.reason());
	}
}
