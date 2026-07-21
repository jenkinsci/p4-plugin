package org.jenkinsci.plugins.p4.unit.review;

import org.jenkinsci.plugins.p4.review.ApproveState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproveStateTest {

	@Test
	void testGetters() {
		assertEquals("needsReview", ApproveState.REVIEW.getId());
		assertEquals("Needs Review", ApproveState.REVIEW.getDescription());
		assertFalse(ApproveState.REVIEW.isCommit());

		assertEquals("approved:commit", ApproveState.COMMIT.getId());
		assertEquals("Approve and Commit", ApproveState.COMMIT.getDescription());
		assertTrue(ApproveState.COMMIT.isCommit());
	}

	@Test
	void testParseIsCaseInsensitive() {
		assertEquals(ApproveState.REVIEW, ApproveState.parse("review"));
		assertEquals(ApproveState.REVIEW, ApproveState.parse("REVIEW"));
		assertEquals(ApproveState.VOTE_UP, ApproveState.parse("Vote_Up"));
	}

	@Test
	void testParseUnknownValueReturnsNull() {
		assertNull(ApproveState.parse("no-such-state"));
	}
}
