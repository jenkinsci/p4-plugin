package org.jenkinsci.plugins.p4.unit.scm;

import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.scm.P4SCMHead;
import org.jenkinsci.plugins.p4.scm.P4SCMRevision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class P4SCMRevisionTest {

	@Test
	void testBuilder() {
		P4Ref ref = new P4PollRef(10, "//depot/main/...");
		P4SCMRevision revision = P4SCMRevision.builder("//depot/main", "main", ref);

		assertSame(ref, revision.getRef());
		assertEquals("10", revision.toString());
	}

	@Test
	void testSwarmBuilder() {
		P4Ref ref = new P4PollRef(10, "//depot/main/...");
		P4SCMRevision revision = P4SCMRevision.swarmBuilder("//depot/main", "main", ref);

		assertSame(ref, revision.getRef());
		assertEquals("10", revision.toString());
	}

	@Test
	void testSwarmBuilderWithReviewId() {
		P4Ref ref = new P4PollRef(10, "//depot/main/...");
		P4SCMRevision revision = P4SCMRevision.swarmBuilder("//depot/main", "main", ref, "19");

		assertSame(ref, revision.getRef());
		assertEquals("10", revision.toString());
	}

	@Test
	void testToStringWhenRefIsNull() {
		P4Ref ref = new P4PollRef(10, "//depot/main/...");
		P4SCMRevision revision = P4SCMRevision.builder("//depot/main", "main", ref);
		P4SCMRevision noRef = new P4SCMRevision((P4SCMHead) revision.getHead(), null);

		assertEquals("undefined", noRef.toString());
	}

	@Test
	void testEqualsAndHashCode() {
		P4Ref ref = new P4PollRef(10, "//depot/main/...");
		P4SCMRevision a = P4SCMRevision.builder("//depot/main", "main", ref);
		P4SCMRevision b = new P4SCMRevision((P4SCMHead) a.getHead(), ref);
		P4SCMRevision differentRef = new P4SCMRevision((P4SCMHead) a.getHead(), new P4PollRef(11, "//depot/main/..."));

		assertEquals(a, a);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, differentRef);
		assertNotEquals(a, "not a revision");
		assertNotEquals(null, a);
	}

	@Test
	void testAddAliasesDoesNotThrow() {
		assertDoesNotThrow(P4SCMRevision::addAliases);
	}
}
