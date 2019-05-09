package org.jenkinsci.plugins.p4.swarmAPI;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.review.ApproveState;
import org.json.JSONArray;

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

		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}

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

		switch (state) {
			case VOTE_UP:
			case VOTE_DOWN:
				return postVote(id, state, description);
			default:
				return patchReview(id, state, description);
		}
	}

	private boolean patchReview(String id, ApproveState state, String description) throws Exception {

		String url = getApiUrl() + "/reviews/" + id + "/state";

		Map<String, Object> parameters = new HashedMap();
		parameters.put("state", state.getId());

		// If commit is used add extra commit parameter
		if (state.isCommit()) {
			parameters.put("commit", "1");
		}

		// If defined, expand description and add parameter
		if (description != null && !description.isEmpty()) {
			parameters.put("description", description);
		}

		// Send PATCH request to Swarm
		HttpResponse<JsonNode> res = Unirest.patch(url)
				.basicAuth(user, ticket)
				.fields(parameters)
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
		
		String url = getApiUrl() + "/comments/";

		Map<String, Object> parameters = new HashedMap();
		parameters.put("topic", "reviews/" + id);
		parameters.put("body", description);

		// Send COMMENT request to Swarm
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
		String url = getBaseUrl() + "/reviews/" + id + "/vote/" + vote;

		// Send VOTE request to Swarm
		HttpResponse<JsonNode> res = Unirest.post(url)
				.basicAuth(user, ticket)
				.asJson();

		if (res.getStatus() == 200) {
			p4.log("Swarm review id: " + id + " voted: " + vote);
			return postComment(id, description);
		} else {
			p4.log("Swarm Error - url: " + url + " code: " + res.getStatus());
			String error = res.getBody().getObject().getString("error");
			p4.log("Swarm error message: " + error);
			throw new SwarmException(res);
		}
	}

	public List<SwarmReviewsAPI.Reviews> getActiveReviews(String project) throws Exception {

		String url = getApiUrl() + "/reviews";

		Map<String, Object> query = new HashMap<>();
		query.put("max", "10");
		query.put("fields", "id,state,changes");
		query.put("project", project);

		HttpResponse<String> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.queryString("state[]", "needsReview")
				.queryString("state[]", "needsRevision")
				.asString();

		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}

		Gson gson = new Gson();
		SwarmReviewsAPI api = gson.fromJson(res.getBody(), SwarmReviewsAPI.class);
		return api.getReviews();
	}

	public SwarmReviewAPI getSwarmReview(String review) throws Exception {

		String url = getApiUrl() + "/reviews/" + review;

		Map<String, Object> query = new HashMap<>();
		query.put("fields", "projects,changes,commits");

		HttpResponse<String> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.asString();

		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}

		Gson gson = new Gson();
		SwarmReviewAPI api = gson.fromJson(res.getBody(), SwarmReviewAPI.class);
		return api;
	}

	public List<SwarmProjectAPI.Branch> getBranchesInProject(String project) throws Exception {

		String url = getApiUrl() + "/projects/" + project;

		Map<String, Object> query = new HashMap<>();
		query.put("fields", "branches");

		HttpResponse<String> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.queryString(query)
				.asString();

		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}

		Gson gson = new Gson();
		SwarmProjectAPI api = gson.fromJson(res.getBody(), SwarmProjectAPI.class);

		List<SwarmProjectAPI.Branch> branches = api.getProject().getBranches();
		return branches;
	}

	public List<String> getProjects() throws Exception {

		String url = getApiUrl() + "/projects";


		HttpResponse<String> res = Unirest.get(url)
				.basicAuth(user, ticket)
				.asString();

		if (res.getStatus() != 200) {
			throw new SwarmException(res);
		}

		Gson gson = new Gson();
		SwarmProjectsAPI api = gson.fromJson(res.getBody(), SwarmProjectsAPI.class);

		List<String> projects = api.getIDs();
		return projects;
	}

	private static class SwarmException extends Exception {
		static final long serialVersionUID = 1;

		public SwarmException(HttpResponse<?> res) {
			super("Swarm error - code: " + res.getStatus() + "\n error: " + res.getStatusText());
		}
	}
}
