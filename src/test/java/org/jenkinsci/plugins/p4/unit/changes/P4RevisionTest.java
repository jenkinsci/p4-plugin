package org.jenkinsci.plugins.p4.unit.changes;

import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P4RevisionTest {

	@Test
	void testLabelRevision() {
		P4Revision rev = new P4Revision("label1");
		assertTrue(rev.isLabel());
		assertEquals(-1, rev.getChange());
		assertEquals("label1", rev.toString());
	}

	@Test
	void testChangeRevision() {
		P4Revision rev = new P4Revision(42);
		assertFalse(rev.isLabel());
		assertEquals(42, rev.getChange());
		assertEquals("42", rev.toString());
	}

	@Test
	void testEqualsAndHashCode() {
		P4Revision a = new P4Revision(42);
		P4Revision b = new P4Revision(42);
		P4Revision c = new P4Revision(43);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
		assertNotEquals(a, "not a revision");
		assertNotEquals(null, a);
	}

	@Test
	void testCompareToEqualRevisions() {
		P4Revision a = new P4Revision(42);
		P4Revision b = new P4Revision(42);
		assertEquals(0, a.compareTo(b));
	}

	@Test
	void testCompareToChangeNumbers() {
		P4Revision low = new P4Revision(5);
		P4Revision high = new P4Revision(10);
		assertTrue(low.compareTo(high) < 0);
		assertTrue(high.compareTo(low) > 0);
	}

	@Test
	void testCompareToWhenArgumentIsNowLabel() {
		P4Revision change = new P4Revision(5);
		P4Revision now = new P4Revision("now");
		assertEquals(-1, change.compareTo(now));
	}

	@Test
	void testCompareToWhenThisIsNowLabel() {
		P4Revision now = new P4Revision("now");
		P4Revision change = new P4Revision(5);
		assertEquals(1, now.compareTo(change));
	}

	@Test
	void testCompareToBetweenTwoNonNowLabels() {
		P4Revision a = new P4Revision("labelA");
		P4Revision b = new P4Revision("labelB");
		assertEquals(0, a.compareTo(b));
	}

	@Test
	void testCompareToThrowsForNonP4Revision() {
		P4Revision rev = new P4Revision(5);
		assertThrows(ClassCastException.class, () -> rev.compareTo("not a revision"));
	}
}
