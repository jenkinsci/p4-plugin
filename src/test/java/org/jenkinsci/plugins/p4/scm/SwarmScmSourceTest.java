package org.jenkinsci.plugins.p4.scm;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadCategory;
import org.jenkinsci.plugins.p4.JsonHttpStubServer;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmScmSourceTest {

	private static final String CREDENTIAL = "credential-id";

	private JsonHttpStubServer stub;
	private SwarmScmSource source;

	@BeforeEach
	void beforeEach() throws Exception {
		stub = new JsonHttpStubServer();
		stub.stub("/api/version", 200, "{\"apiVersions\":[\"11\"]}");

		ConnectionHelper p4 = mock(ConnectionHelper.class);
		when(p4.getSwarm()).thenReturn(stub.getUrl());
		when(p4.getUser()).thenReturn("jenkins");
		when(p4.getTicket()).thenReturn("dummy-ticket");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		source = new SwarmScmSource(CREDENTIAL, "none", "jenkins-${NODE_NAME}-${JOB_NAME}");
		source.setProject("myproject");
		source.setSwarm(swarm);
	}

	@AfterEach
	void afterEach() {
		stub.close();
	}

	@Test
	void testGetTagsBuildsAChangeRequestHeadPerBranchInEachActiveReview() throws Exception {
		stub.stub("/api/v11/reviews", 200,
				"{\"data\":{\"reviews\":[{\"id\":123,\"changes\":[10],\"author\":\"bob\"}]}}");
		stub.stub("/api/v11/reviews/123", 200,
				"{\"data\":{\"reviews\":[{\"changes\":[10],\"commits\":[],"
						+ "\"projects\":{\"myproject\":[\"main\"]},\"author\":\"bob\"}]}}");
		stub.stub("/api/v11/projects/myproject", 200,
				"{\"data\":{\"projects\":[{\"id\":\"myproject\",\"members\":[],\"owners\":[],"
						+ "\"branches\":[{\"id\":\"main\",\"name\":\"main\",\"paths\":[\"//depot/main/...\"]}]}]}}");

		List<P4SCMHead> tags = source.getTags(mock(TaskListener.class));

		assertEquals(1, tags.size());
	}

	@Test
	void testGetBrowserReturnsSwarmBrowserUsingSwarmBaseUrl() {
		P4Browser browser = source.getBrowser();

		assertInstanceOf(SwarmBrowser.class, browser);
		assertEquals(stub.getUrl(), browser.getUrl());
	}

	@Test
	void testGetTagsExcludesBranchesMatchingExcludesPattern() throws Exception {
		source.setExcludes("main");
		stub.stub("/api/v11/reviews", 200,
				"{\"data\":{\"reviews\":[{\"id\":123,\"changes\":[10],\"author\":\"bob\"}]}}");
		stub.stub("/api/v11/reviews/123", 200,
				"{\"data\":{\"reviews\":[{\"changes\":[10],\"commits\":[],"
						+ "\"projects\":{\"myproject\":[\"main\"]},\"author\":\"bob\"}]}}");

		List<P4SCMHead> tags = source.getTags(mock(TaskListener.class));

		assertEquals(0, tags.size());
	}

	@Test
	void testIsCategoryEnabledAlwaysReturnsTrue() {
		assertTrue(source.isCategoryEnabled(mock(SCMHeadCategory.class)));
	}

	@Test
	void testFindIncludeReturnsFalseWhenSwarmLookupFails() {
		// No stub registered for /api/v11/projects/myproject, so the lookup 404s
		// and getBranchesInProject throws - findInclude should swallow it and return false.
		assertFalse(source.findInclude("//depot/main"));
	}
}
