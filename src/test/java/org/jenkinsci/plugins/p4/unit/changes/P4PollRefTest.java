package org.jenkinsci.plugins.p4.unit.changes;

import com.perforce.p4java.core.file.IFileSpec;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P4PollRefTest {

	@Test
	void testGetters() {
		P4PollRef ref = new P4PollRef(10, "//depot/...");

		assertFalse(ref.isLabel());
		assertFalse(ref.isCommit());
		assertEquals(10, ref.getChange());
		assertEquals("//depot/...", ref.getPollPath());
		assertEquals("10", ref.toString());
	}

	@Test
	void testEqualsAndHashCode() {
		P4PollRef a = new P4PollRef(10, "//depot/...");
		P4PollRef b = new P4PollRef(10, "//depot/...");
		P4PollRef differentChange = new P4PollRef(11, "//depot/...");
		P4PollRef differentPath = new P4PollRef(10, "//other/...");

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertEquals(a, a);
		assertNotEquals(a, differentChange);
		assertNotEquals(a, differentPath);
		assertNotEquals(a, "not a ref");
		assertNotEquals(null, a);
	}

	@Test
	void testCompareTo() {
		P4PollRef low = new P4PollRef(5, "//depot/...");
		P4PollRef high = new P4PollRef(10, "//depot/...");
		P4PollRef lowCopy = new P4PollRef(5, "//depot/...");

		assertEquals(0, low.compareTo(lowCopy));
		assertTrue(low.compareTo(high) < 0);
		assertTrue(high.compareTo(low) > 0);
	}

	@Test
	void testCompareToThrowsForNonP4PollRef() {
		P4PollRef ref = new P4PollRef(5, "//depot/...");
		assertThrows(ClassCastException.class, () -> ref.compareTo("not a ref"));
	}

	@Test
	void testGetFilesDelegatesToConnectionHelper() throws Exception {
		P4PollRef ref = new P4PollRef(10, "//depot/...");
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		List<IFileSpec> files = List.of(mock(IFileSpec.class));
		when(p4.getChangeFiles(10, 5)).thenReturn(files);

		assertSame(files, ref.getFiles(p4, 5));
	}
}
