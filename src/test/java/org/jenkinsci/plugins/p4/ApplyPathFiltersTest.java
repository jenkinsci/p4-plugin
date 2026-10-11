package org.jenkinsci.plugins.p4;

import com.perforce.p4java.core.file.FileAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * PerforceScm.applyPathFilters() must exclude a change only when all of its files lie under a
 * filter path, and must list every kept change exactly once (regardless of its number of files).
 */
class ApplyPathFiltersTest {

	private static final String EXCLUDE = "//depot/exclude/";

	private MockedStatic<Jenkins> jenkinsStatic;

	// P4ChangeEntry's constructor looks up the file count limit via Jenkins.get(); it copes with null.
	@BeforeEach
	void mockJenkins() {
		jenkinsStatic = mockStatic(Jenkins.class);
		jenkinsStatic.when(Jenkins::get).thenReturn(null);
	}

	@AfterEach
	void closeJenkinsMock() {
		jenkinsStatic.close();
	}

	private static P4ChangeEntry change(String... paths) {
		P4ChangeEntry entry = new P4ChangeEntry((P4ChangeSet) null);
		for (String path : paths) {
			entry.addAffectedFiles(new P4AffectedFile(path, "1", FileAction.EDIT));
		}
		return entry;
	}

	private static List<Filter> pathFilters(String... paths) {
		return java.util.Arrays.stream(paths).map(p -> (Filter) new FilterPathImpl(p)).toList();
	}

	@Test
	void multiFileChangeOutsideFilterIsListedOnce() {
		String[] files = new String[203];
		for (int i = 0; i < files.length; i++) {
			files[i] = "//depot/keep/file" + i + ".txt";
		}
		P4ChangeEntry big = change(files);

		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(big), pathFilters(EXCLUDE));

		assertEquals(1, result.size(), "a change must not be repeated once per affected file");
		assertSame(big, result.get(0));
	}

	@Test
	void changeEntirelyUnderFilterIsExcluded() {
		P4ChangeEntry excluded = change(EXCLUDE + "a.txt", EXCLUDE + "sub/b.txt");
		P4ChangeEntry kept = change("//depot/keep/c.txt");

		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(excluded, kept), pathFilters(EXCLUDE));

		assertEquals(List.of(kept), result);
	}

	@Test
	void changeSpanningFilterAndOtherPathIsKeptOnce() {
		P4ChangeEntry mixed = change(EXCLUDE + "a.txt", "//depot/keep/b.txt", "//depot/keep/c.txt");

		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(mixed), pathFilters(EXCLUDE));

		assertEquals(List.of(mixed), result);
	}

	@Test
	void multipleFiltersExcludeOnlyWhenEveryFileIsCovered() {
		P4ChangeEntry covered = change("//depot/a/x.txt", "//depot/b/y.txt");
		P4ChangeEntry partly = change("//depot/a/x.txt", "//depot/c/z.txt");
		P4ChangeEntry none = change("//depot/c/z.txt");

		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(covered, partly, none),
				pathFilters("//depot/a/", "//depot/b/"));

		assertEquals(List.of(partly, none), result, "each change at most once, in the original order");
	}

	@Test
	void everyChangeExcludedGivesEmptyList() {
		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(change(EXCLUDE + "a.txt")),
				pathFilters(EXCLUDE));

		assertTrue(result.isEmpty());
	}

	@Test
	void withoutPathFiltersTheListIsReturnedUnchanged() {
		List<P4ChangeEntry> entries = List.of(change(EXCLUDE + "a.txt"));

		assertSame(entries, PerforceScm.applyPathFilters(entries, List.of()));
		assertSame(entries, PerforceScm.applyPathFilters(entries, List.of(new FilterUserImpl("someone"))));
	}

	@Test
	void changeWithoutKnownFilesIsKept() {
		P4ChangeEntry noFiles = change();
		P4ChangeEntry truncated = change(EXCLUDE + "a.txt");
		truncated.setFileLimit(true);

		List<P4ChangeEntry> result = PerforceScm.applyPathFilters(List.of(noFiles, truncated), pathFilters(EXCLUDE));

		assertEquals(List.of(noFiles, truncated), result);
	}
}
