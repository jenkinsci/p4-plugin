package org.jenkinsci.plugins.p4.swarmAPI;

import org.jenkinsci.plugins.p4.JsonHttpStubServer;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.review.ApproveState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmHelperTest {

	private JsonHttpStubServer stub;
	private ConnectionHelper p4;

	@BeforeEach
	void beforeEach() throws Exception {
		stub = new JsonHttpStubServer();
		p4 = mock(ConnectionHelper.class);
		when(p4.getSwarm()).thenReturn(stub.getUrl());
		when(p4.getUser()).thenReturn("jenkins");
		when(p4.getTicket()).thenReturn("dummy-ticket");
		stub.stub("/api/version", 200, "{\"apiVersions\":[\"11\"]}");
	}

	@AfterEach
	void afterEach() {
		stub.close();
	}

	@Test
	void testConstructorThrowsWhenApiVersionUnsupported() {
		stub.stub("/api/version", 200, "{\"apiVersions\":[\"99\"]}");

		Exception e = assertThrows(Exception.class, () -> new SwarmHelper(p4, "11"));
		assertTrue(e.getMessage().contains("Swarm does not support API Version: 11"));
	}

	@Test
	void testConstructorThrowsWhenVersionCheckFails() {
		stub.stub("/api/version", 500, "{\"error\":\"boom\"}");

		assertThrows(Exception.class, () -> new SwarmHelper(p4, "11"));
	}

	@Test
	void testGetBaseUrl() throws Exception {
		SwarmHelper swarm = new SwarmHelper(p4, "11");
		assertEquals(stub.getUrl(), swarm.getBaseUrl());
	}

	@Test
	void testApproveReviewReturnsFalseForEmptyOrNullId() throws Exception {
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertFalse(swarm.approveReview("", ApproveState.APPROVED, null));
		assertFalse(swarm.approveReview(null, ApproveState.APPROVED, null));
	}

	@Test
	void testApproveReviewReturnsFalseForUnexpandedReviewIdParameter() throws Exception {
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertFalse(swarm.approveReview("P4_REVIEW", ApproveState.APPROVED, null));
	}

	@Test
	void testApproveReviewPatchesReviewForNonVoteState() throws Exception {
		stub.stub("/api/v11/reviews/123/transitions", 200, "{}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertTrue(swarm.approveReview("123", ApproveState.APPROVED, null));
	}

	@Test
	void testApproveReviewThrowsWhenPatchReviewFails() throws Exception {
		stub.stub("/api/v11/reviews/123/transitions", 500, "{\"error\":\"denied\"}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		Exception e = assertThrows(Exception.class, () -> swarm.approveReview("123", ApproveState.APPROVED, null));
		assertTrue(e.getMessage().contains("Swarm error"));
	}

	@Test
	void testApproveReviewVotesAndComments() throws Exception {
		stub.stub("/api/v11/reviews/123/vote", 200, "{\"data\":{\"vote\":[\"up\"]}}");
		stub.stub("/api/v11/comments/reviews/123", 200, "{}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertTrue(swarm.approveReview("123", ApproveState.VOTE_UP, "nice work"));
	}

	@Test
	void testApproveReviewThrowsWhenPostCommentFails() throws Exception {
		stub.stub("/api/v11/reviews/123/vote", 200, "{\"data\":{\"vote\":[\"up\"]}}");
		stub.stub("/api/v11/comments/reviews/123", 500, "{\"error\":\"denied\"}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		Exception e = assertThrows(Exception.class,
				() -> swarm.approveReview("123", ApproveState.VOTE_UP, "nice work"));
		assertTrue(e.getMessage().contains("Swarm error"));
	}

	@Test
	void testApproveReviewVoteOnOwnReviewSkipsVoteButStillComments() throws Exception {
		stub.stub("/api/v11/reviews/123/vote", 200, "{\"data\":{\"vote\":[]}}");
		stub.stub("/api/v11/comments/reviews/123", 200, "{}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertTrue(swarm.approveReview("123", ApproveState.VOTE_UP, "nice work"));
	}

	@Test
	void testApproveReviewVoteWithoutDescriptionSkipsComment() throws Exception {
		stub.stub("/api/v11/reviews/123/vote", 200, "{\"data\":{\"vote\":[\"up\"]}}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertTrue(swarm.approveReview("123", ApproveState.VOTE_UP, null));
	}

	@Test
	void testApproveReviewThrowsWhenVoteFails() throws Exception {
		stub.stub("/api/v11/reviews/123/vote", 500, "{\"error\":\"denied\"}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		assertThrows(Exception.class, () -> swarm.approveReview("123", ApproveState.VOTE_UP, null));
	}

	@Test
	void testGetActiveReviews() throws Exception {
		stub.stub("/api/v11/reviews", 200,
				"{\"data\":{\"reviews\":[{\"id\":123,\"changes\":[10,11],\"author\":\"bob\"}]}}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		List<SwarmReviewsAPI.Reviews> reviews = swarm.getActiveReviews("myproject");

		assertEquals(1, reviews.size());
		assertEquals(123, reviews.get(0).getId());
		assertEquals("bob", reviews.get(0).getAuthor());
	}

	@Test
	void testGetSwarmReview() throws Exception {
		stub.stub("/api/v11/reviews/123", 200,
				"{\"data\":{\"reviews\":[{\"changes\":[1],\"commits\":[2],"
						+ "\"projects\":{\"myproject\":[\"main\"]},\"author\":\"bob\"}]}}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		SwarmReviewAPI api = swarm.getSwarmReview("123");

		assertEquals(1, api.getReview().size());
		assertEquals("bob", api.getReview().get(0).getAuthor());
	}

	@Test
	void testGetBranchesInProjectLowercasesProjectNameInUrl() throws Exception {
		stub.stub("/api/v11/projects/myproject", 200,
				"{\"data\":{\"projects\":[{\"id\":\"myproject\",\"members\":[],\"owners\":[],"
						+ "\"branches\":[{\"id\":\"main\",\"name\":\"main\",\"paths\":[\"//depot/main/...\"]}]}]}}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		List<SwarmProjectAPI.Branch> branches = swarm.getBranchesInProject("MyProject");

		assertEquals(1, branches.size());
		assertEquals("main", branches.get(0).getName());
	}

	@Test
	void testGetProjectsFiltersByUser() throws Exception {
		stub.stub("/api/v11/projects", 200,
				"{\"data\":{\"projects\":["
						+ "{\"id\":\"proj1\",\"members\":[\"jenkins\"],\"owners\":[],\"branches\":[]},"
						+ "{\"id\":\"proj2\",\"members\":[],\"owners\":[\"someoneelse\"],\"branches\":[]}"
						+ "]}}");
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		List<String> projects = swarm.getProjects();

		assertEquals(List.of("proj1"), projects);
	}
}
