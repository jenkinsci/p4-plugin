package org.jenkinsci.plugins.p4.swarmAPI;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.perforce.p4java.exception.P4JavaException;
import hudson.EnvVars;
import org.apache.commons.collections.map.HashedMap;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.review.ApproveState;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.json.JSONArray;

import java.util.Map;

public class SwarmHelper {

	private final ConnectionHelper p4;
	private final String swarmUrl;
	private String user;
	private String ticket;

	public SwarmHelper(ConnectionHelper p4) throws P4JavaException {
		this.p4 = p4;
		this.swarmUrl = p4.getSwarm();
		this.user = p4.getUser();
		this.ticket = p4.getTicket();
	}

	public boolean checkVersion(String ver) {
		try {
			String url = swarmUrl + "/api/version";

			HttpResponse<JsonNode> res = Unirest.get(url)
					.basicAuth(user, ticket)
					.asJson();

			if (res.getStatus() != 200) {
				p4.log("Swarm error - url: " + url + " error: " + res.getStatusText());
				return false;
			}
			JSONArray json = res.getBody().getObject().getJSONArray("apiVersions");
			for (int i = 0; i < json.length(); i++) {
				String v = String.valueOf(json.get(i));
				if(ver.equals(v)) {
					return true;
				}
			}
		} catch (UnirestException e) {
			p4.log("Swarm connection error: " + e.getMessage());
			return false;
		}
		p4.log("Swarm does not support API Version: " + ver);
		return false;
	}

	public boolean approveReview(ConnectionHelper p4, EnvVars env, String id, ApproveState state) throws Exception {

		// Exit early if Swarm API version not supported
		if(!checkVersion("4")) {
			p4.log( "Unable to connect to Swarm.");
			return false;
		}

		// Exit early if review ID is not valid
		if (id == null || id.isEmpty()) {
			p4.log("Review ID is empty or null!");
			return false;
		}

		// Expand review ID using environment
		Expand expand = new Expand(env);
		id = expand.format(id, false);

		if("P4_REVIEW".equalsIgnoreCase(id)) {
			p4.log("Environment for Review ID not found!");
			return false;
		}

		String url = p4.getSwarm() + "/api/v4/reviews/" + id + "/state";

		Map<String, Object> parameters = new HashedMap();
		parameters.put("state", state.getId());
		if(state.isCommit()) {
			parameters.put("commit", true);
		}

		HttpResponse<JsonNode> res = Unirest.patch(url)
				.basicAuth(p4.getUser(), p4.getTicket())
				.fields(parameters)
				.asJson();

		if(res.getStatus() == 200) {
			p4.log("Swarm review id: " + id + " updated: " + state.getDescription());
			return true;
		} else {
			p4.log( "Swarm Error - url: " + url + " code: " + res.getStatus());
			String error = res.getBody().getObject().getString("error");
			p4.log("Swarm error message: " + error);
		}

		return false;
	}
}
