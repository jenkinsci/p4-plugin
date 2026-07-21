package org.jenkinsci.plugins.p4.scm;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractP4ScmSourceTest {

	private static final String CREDENTIAL = "credential-id";

	private BranchesScmSource newSource() {
		return new BranchesScmSource(CREDENTIAL, "//depot/main/...", "none", "jenkins-${NODE_NAME}-${JOB_NAME}");
	}

	@Test
	void testBasicAccessors() {
		BranchesScmSource source = newSource();

		assertEquals(CREDENTIAL, source.getCredential());
		assertEquals("//depot/main/...", source.getIncludes());
		assertEquals("none", source.getCharset());
		assertEquals("jenkins-${NODE_NAME}-${JOB_NAME}", source.getFormat());
	}

	@Test
	void testExcludesDefaultsWhenNotSet() {
		BranchesScmSource source = newSource();
		assertEquals(AbstractP4ScmSource.defaultExcludes, source.getExcludes());

		source.setExcludes("");
		assertEquals(AbstractP4ScmSource.defaultExcludes, source.getExcludes());

		source.setExcludes("//depot/exclude/...");
		assertEquals("//depot/exclude/...", source.getExcludes());
	}

	@Test
	void testPopulateAndFilter() {
		BranchesScmSource source = newSource();
		assertNull(source.getPopulate());
		assertNull(source.getFilter());

		Populate populate = new AutoCleanImpl();
		source.setPopulate(populate);
		assertSame(populate, source.getPopulate());

		List<org.jenkinsci.plugins.p4.filters.Filter> filters = List.of(new FilterPerChangeImpl(true));
		source.setFilter(filters);
		assertEquals(filters, source.getFilter());
	}

	@Test
	void testTraitsDefaultsToEmptyAndIsUnmodifiable() {
		BranchesScmSource source = newSource();
		assertTrue(source.getTraits().isEmpty());
		assertTrue(FilterPerChangeImpl.isActive(source.getFilter()) == false);
	}

	@Test
	void testGetIncludePathsSplitsOnNewlines() {
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, "//depot/a/...\n//depot/b/...", "none", "format");
		assertEquals(Arrays.asList("//depot/a/...", "//depot/b/..."), source.getIncludePaths());
	}

	@Test
	void testToLinesHandlesNull() {
		BranchesScmSource source = newSource();
		assertEquals(Collections.emptyList(), source.toLines(null));
	}

	@Test
	void testFindInclude() {
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, "//depot/main/...", "none", "format");

		assertTrue(source.findInclude("//depot/main/..."));
		assertTrue(source.findInclude("//depot/main/*"));
		assertFalse(source.findInclude("//depot/other/..."));
	}

	@Test
	void testPathContainsFolderPropertyVar() {
		BranchesScmSource source = newSource();

		assertFalse(source.pathContainsFolderPropertyVar(null));
		assertFalse(source.pathContainsFolderPropertyVar(Collections.emptyList()));
		assertFalse(source.pathContainsFolderPropertyVar(List.of("//depot/main/...")));
		assertTrue(source.pathContainsFolderPropertyVar(List.of("//depot/${MY_PROP}/...")));
	}

	@Test
	void testGetPropertyReturnsNullWhenAbsentOrEmpty() {
		BranchesScmSource source = newSource();

		JSONObject empty = new JSONObject();
		assertNull(source.getProperty(empty, ReviewProp.P4_CHANGE));

		JSONObject blank = new JSONObject();
		blank.element(ReviewProp.P4_CHANGE.getProp(), "");
		assertNull(source.getProperty(blank, ReviewProp.P4_CHANGE));

		JSONObject present = new JSONObject();
		present.element(ReviewProp.P4_CHANGE.getProp(), "123");
		assertEquals("123", source.getProperty(present, ReviewProp.P4_CHANGE));
	}

	@Test
	void testGetScriptPathOrDefaultFallsBackToJenkinsfile() {
		BranchesScmSource source = newSource();
		assertEquals("Jenkinsfile", source.getScriptPathOrDefault());
	}

	@Test
	void testGetRevisionUsesLatestChangeWhenFound() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("100");
		when(p4.getHeadLimit()).thenReturn(50L);
		when(p4.getClientHead(new P4ChangeRef(50), new P4ChangeRef(100))).thenReturn(75L);

		P4Path path = new P4Path("//depot/main");
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(75L, revision.getRef().getChange());
	}

	@Test
	void testGetRevisionFallsBackToClientHeadWhenNoChangeFound() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("100");
		when(p4.getHeadLimit()).thenReturn(50L);
		when(p4.getClientHead(new P4ChangeRef(50), new P4ChangeRef(100))).thenReturn(0L);
		when(p4.getClientHead()).thenReturn(42L);

		P4Path path = new P4Path("//depot/main");
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(42L, revision.getRef().getChange());
	}

	@Test
	void testGetRevisionConsidersExtraMappingsAndSkipsExcluded() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("100");
		when(p4.getHeadLimit()).thenReturn(50L);
		when(p4.getClientHead(new P4ChangeRef(50), new P4ChangeRef(100))).thenReturn(60L);
		when(p4.getHead("//depot/extra/...", new P4ChangeRef(50), new P4ChangeRef(100))).thenReturn(80L);

		P4Path path = new P4Path("//depot/main");
		path.setMappings(List.of("-//depot/excluded/...", "//depot/extra/..."));
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(80L, revision.getRef().getChange());
	}

	@Test
	void testGetRevisionUsesPathRevisionAsToLimitWhenSet() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("999");
		when(p4.getHeadLimit()).thenReturn(10L);
		when(p4.getClientHead(new P4ChangeRef(190), new P4ChangeRef(200))).thenReturn(195L);

		P4Path path = new P4Path("//depot/main");
		path.setRevision("200");
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(195L, revision.getRef().getChange());
	}

	@Test
	void testGetRevisionFallsBackToCounterWhenRevisionNotNumeric() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("100");
		when(p4.getHeadLimit()).thenReturn(50L);
		when(p4.getClientHead(new P4ChangeRef(50), new P4ChangeRef(100))).thenReturn(70L);

		P4Path path = new P4Path("//depot/main");
		path.setRevision("not-a-number");
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(70L, revision.getRef().getChange());
	}

	@Test
	void testGetRevisionWhenRangeLimitNotPositive() throws Exception {
		BranchesScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		when(p4.getCounter("change")).thenReturn("10");
		when(p4.getHeadLimit()).thenReturn(50L);
		when(p4.getClientHead(null, new P4ChangeRef(10))).thenReturn(5L);

		P4Path path = new P4Path("//depot/main");
		P4SCMHead head = new P4SCMHead("main", path);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertEquals(5L, revision.getRef().getChange());
	}
}
