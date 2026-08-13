package org.jenkinsci.plugins.p4.swarmAPI;

import com.google.gson.Gson;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONObject;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.review.ApproveState;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwarmHelper {

	private final ConnectionHelper p4;
	private final String version;
	private final String base;
	private final String user;
	private final String ticket;

	public SwarmHelper(ConnectionHelper p4, String version) throws Exception {
		this.p4 = p4;
		this.version = version;
		this.base = p4.getSwarm();
		this.user = p4.getUser();
		this.ticket = p4.getTicket();

		if (!checkVersion(version)) {
			throw new Exception("Swarm does not support API Version: " + version);
		}
	}

	public String getBaseUrl() {
		return base;
	}

	private String getApiUrl() {
		return base + "/api/v" + version;
	}

	private boolean checkVersion(String ver) throws Exception {

		String url = base + "/api/version";

		HttpResponse<JsonNode> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.asJson();

		handleAPICallExceptions(res);

		JSONArray json = res.getBody().getObject().getJSONArray("apiVersions");
		for (int i = 0; i < json.length(); i++) {
			String v = String.valueOf(json.get(i));
			if (ver.equals(v)) {
				return true;
			}
		}

		return false;
	}

	public boolean approveReview(String id, ApproveState state, String description) throws Exception {

		// Exit early if review ID is not valid
		if (id == null || id.isEmpty()) {
			p4.log("Review ID is empty or null!");
			return false;
		}

		// Expand review ID using environment
		if ("P4_REVIEW".equalsIgnoreCase(id)) {
			p4.log("Environment for Review ID not found!");
			return false;
		}

		return switch (state) {
			case VOTE_UP, VOTE_DOWN -> postVote(id, state, description);
			default -> patchReview(id, state, description);
		};
	}

	private boolean patchReview(String id, ApproveState state, String description) throws Exception {
		String url = getApiUrl() + "/reviews/" + id + "/transitions";
		JSONObject body = new JSONObject();
		body.put("transition", state.getId());

		HttpResponse<JsonNode> res = Unirest.post(url)
				.basicAuth(user, ticket)
				.body(body)
				.asJson();

		if (res.getStatus() == 200) {
			p4.log("Swarm review id: " + id + " updated: " + state.getDescription());
			return true;
		} else {
			p4.log("Swarm Error - url: " + url + " code: " + res.getStatus());
			String error = res.getBody().getObject().getString("error");
			p4.log("Swarm error message: " + error);
			throw new SwarmException(res);
		}
	}

	private boolean postComment(String id, String description) throws Exception {
		if (StringUtils.isEmpty(description)) {
			return true;
		}

		String url = getApiUrl() + "/comments/reviews/" + id;
		Map<String, Object> parameters = new HashedMap();
		parameters.put("body", description);

		HttpResponse<JsonNode> res = Unirest.post(url)
				.basicAuth(user, ticket)
				.fields(parameters)
				.asJson();

		if (res.getStatus() == 200) {
			p4.log("Swarm review id: " + id + " comment: " + description);
			return true;
		} else {
			p4.log("Swarm Error - url: " + url + " code: " + res.getStatus());
			String error = res.getBody().getObject().getString("error");
			p4.log("Swarm error message: " + error);
			throw new SwarmException(res);
		}
	}

	private boolean postVote(String id, ApproveState state, String description) throws Exception {
		String vote = state.getId();
		String url = getApiUrl() + "/reviews/" + id + "/vote";

		JSONObject body = new JSONObject();
		body.put("vote", vote);

		// Send VOTE request to Swarm
		HttpResponse<JsonNode> res = Unirest.post(url)
				.basicAuth(user, ticket)
				.body(body)
				.asJson();

		JSONObject responseBody = res.getBody().getObject();
		if (res.getStatus() == 200) {
			JSONObject data = (JSONObject) responseBody.get("data");
			Object updatedVote = data.get("vote");
			if (updatedVote instanceof JSONArray && ((JSONArray) updatedVote).isEmpty()) {
				p4.log("A user cannot vote on a review they have created themselves. User: " + user);
			} else {
				p4.log("Swarm review id: " + id + " voted: " + vote);
			}
			return postComment(id, description);
		} else {
			p4.log("Swarm Error - url: " + url + " code: " + res.getStatus());
			String error = responseBody.getString("error");
			p4.log("Swarm error message: " + error);
			throw new SwarmException(res);
		}
	}

	public List<SwarmReviewsAPI.Reviews> getActiveReviews(String project) throws Exception {
		String url = getApiUrl() + "/reviews";
		Map<String, Object> query = new HashMap<>();
		query.put("max", "10");
		query.put("fields", "id,state,changes,author");
		query.put("project", project);
		HttpResponse<JsonNode> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.queryString("state[]", "needsReview")
				.queryString("state[]", "needsRevision")
				.asJson();
		handleAPICallExceptions(res);
		JSONObject data = getDataFromSwarmResponse(res);
		Gson gson = new Gson();
		SwarmReviewsAPI api = gson.fromJson(data.toString(), SwarmReviewsAPI.class);
		return api.getReviews();
	}

	public SwarmReviewAPI getSwarmReview(String review) throws Exception {
		String url = getApiUrl() + "/reviews/" + review;
		Map<String, Object> query = new HashMap<>();
		query.put("fields", "projects,changes,commits,author");
		HttpResponse<JsonNode> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.asJson();
		handleAPICallExceptions(res);
		Gson gson = new Gson();
		SwarmReviewAPI api = gson.fromJson(getDataFromSwarmResponse(res).toString(), SwarmReviewAPI.class);
		return api;
	}

	public List<SwarmProjectAPI.Branch> getBranchesInProject(String project) throws Exception {
		String url = getApiUrl() + "/projects/" + project.toLowerCase();
		Map<String, Object> query = new HashMap<>();
		query.put("fields", "branches");
		HttpResponse<JsonNode> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.asJson();

		handleAPICallExceptions(res);
		JSONObject data = getDataFromSwarmResponse(res);
		Gson gson = new Gson();
		SwarmProjectAPI api = gson.fromJson(data.toString(), SwarmProjectAPI.class);
		return api.getProject().get(0).getBranches();
	}

	/**
	 * Get a list of project 'ids' from swarm where the current users is a member or owner
	 *
	 * @return A filtered list of projects
	 * @throws Exception API or connection errors
	 */
	public List<String> getProjects() throws Exception {
		String url = getApiUrl() + "/projects";
		Map<String, Object> query = new HashMap<>();
		query.put("fields", "id,members,owners");

		HttpResponse<JsonNode> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.asJson();
		handleAPICallExceptions(res);
		Gson gson = new Gson();
		SwarmProjectsAPI api = gson.fromJson(getDataFromSwarmResponse(res).toString(), SwarmProjectsAPI.class);
		return api.getIDsByUser(user);
	}

	private static JSONObject getDataFromSwarmResponse(HttpResponse<JsonNode> res) {
		return res.getBody()
				.getObject()
				.getJSONObject("data");
	}

	private static void handleAPICallExceptions(HttpResponse<JsonNode> res) throws SwarmException {
		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}
	}

	private static class SwarmException extends Exception {
		@Serial
		private static final long serialVersionUID = 1;

		public SwarmException(HttpResponse<?> res) {
			super("Swarm error - code: " + res.getStatus() + "\n error: " + res.getStatusText());
		}
	}
}
