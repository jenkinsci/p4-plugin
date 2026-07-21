package org.jenkinsci.plugins.p4.unit.changes;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.graph.ICommit;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P4GraphRefTest {

	@Test
	void testCommitConstructor() {
		ICommit commit = mock(ICommit.class);
		Date date = new Date(1_600_000_000_000L);
		when(commit.getCommitterDate()).thenReturn(date);
		when(commit.getCommit()).thenReturn("abc123");

		P4GraphRef ref = new P4GraphRef("myrepo", commit);

		assertEquals("myrepo", ref.getRepo());
		assertEquals("abc123", ref.getSha());
		assertEquals(date.getTime(), ref.getDate());
		assertFalse(ref.isLabel());
		assertTrue(ref.isCommit());
		assertEquals(-1L, ref.getChange());
		assertEquals("myrepo@abc123", ref.toString());
	}

	@Test
	void testConnectionHelperConstructorWithValidId() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		ICommit commit = mock(ICommit.class);
		Date date = new Date(1_600_000_000_000L);
		when(commit.getCommitterDate()).thenReturn(date);
		when(p4.getGraphCommit("sha1", "myrepo")).thenReturn(commit);

		P4GraphRef ref = new P4GraphRef(p4, "myrepo@sha1");

		assertEquals("myrepo", ref.getRepo());
		assertEquals("sha1", ref.getSha());
		assertEquals(date.getTime(), ref.getDate());
	}

	@Test
	void testConnectionHelperConstructorWithNullOrMalformedId() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);

		for (String id : new String[]{null, "", "no-at-sign", "a@b@c"}) {
			P4GraphRef ref = new P4GraphRef(p4, id);
			assertNull(ref.getRepo());
			assertNull(ref.getSha());
			assertEquals(0L, ref.getDate());
		}
	}

	@Test
	void testEqualsAndHashCode() {
		ICommit commit = mock(ICommit.class);
		when(commit.getCommitterDate()).thenReturn(new Date());
		when(commit.getCommit()).thenReturn("sha1");

		P4GraphRef a = new P4GraphRef("repo", commit);
		P4GraphRef b = new P4GraphRef("repo", commit);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, "not a ref");
	}

	@Test
	void testCompareToUsesEqualsBeforeDate() {
		ICommit commitOld = mock(ICommit.class);
		when(commitOld.getCommitterDate()).thenReturn(new Date(1000));
		when(commitOld.getCommit()).thenReturn("sha1");

		ICommit commitNew = mock(ICommit.class);
		when(commitNew.getCommitterDate()).thenReturn(new Date(999_999));
		when(commitNew.getCommit()).thenReturn("sha1");

		P4GraphRef a = new P4GraphRef("repo", commitOld);
		P4GraphRef b = new P4GraphRef("repo", commitNew);

		assertEquals(0, a.compareTo(b));
	}

	@Test
	void testCompareToByDateWhenNotEqual() {
		ICommit earlier = mock(ICommit.class);
		when(earlier.getCommitterDate()).thenReturn(new Date(1000));
		when(earlier.getCommit()).thenReturn("sha1");

		ICommit later = mock(ICommit.class);
		when(later.getCommitterDate()).thenReturn(new Date(2000));
		when(later.getCommit()).thenReturn("sha2");

		P4GraphRef a = new P4GraphRef("repo", earlier);
		P4GraphRef b = new P4GraphRef("repo", later);

		assertTrue(a.compareTo(b) < 0);
		assertTrue(b.compareTo(a) > 0);
	}

	@Test
	void testCompareToThrowsForNonP4GraphRef() {
		ICommit commit = mock(ICommit.class);
		when(commit.getCommitterDate()).thenReturn(new Date());
		when(commit.getCommit()).thenReturn("sha1");
		P4GraphRef ref = new P4GraphRef("repo", commit);

		assertThrows(ClassCastException.class, () -> ref.compareTo("not a ref"));
	}

	@Test
	void testGetFilesReturnsSubList() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		IFileSpec f1 = mock(IFileSpec.class);
		IFileSpec f2 = mock(IFileSpec.class);
		IFileSpec f3 = mock(IFileSpec.class);
		when(p4.getCommitFiles("repo", "sha1")).thenReturn(List.of(f1, f2, f3));

		ICommit commit = mock(ICommit.class);
		when(commit.getCommitterDate()).thenReturn(new Date());
		when(commit.getCommit()).thenReturn("sha1");
		P4GraphRef ref = new P4GraphRef("repo", commit);

		List<IFileSpec> result = ref.getFiles(p4, 2);

		assertEquals(List.of(f1, f2), result);
	}
}
