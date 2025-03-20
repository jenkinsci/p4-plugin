package org.jenkinsci.plugins.p4.review;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.MatrixRun;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.workflow.source.P4SwarmUpdateAction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ReviewNotifier extends RunListener<Run> {

	private static final Logger logger = Logger.getLogger(ReviewNotifier.class.getName());

	@Override
	public void onCompleted(Run run, @NonNull TaskListener listener) {
		if (run instanceof MatrixRun) {
			return;
		}
		logger.fine("ReviewNotifier: onCompleted");
		try {
			Result result = run.getResult();
			if (result == null) {
				logger.warning("Cannot notify onCompleted - job Result is null!");
				return;
			}

			String buildURL = getBuildURL(run);
			EnvVars env = run.getEnvironment(listener);
			String updateCallback = env.get(ReviewProp.SWARM_UPDATE.getProp());
			if (StringUtils.isNotEmpty(updateCallback)) {
				String status = (result.equals(Result.SUCCESS)) ? "pass" : "fail";
				List<P4SwarmUpdateAction> actions = run.getActions(P4SwarmUpdateAction.class);
				List<String> message = getUpdateMessage(actions);
				notifySwarmUpdate(updateCallback, status, message, buildURL);
			} else {
				String callbackURL = (result.equals(Result.SUCCESS))
						? env.get(ReviewProp.SWARM_PASS.getProp())
						: env.get(ReviewProp.SWARM_FAIL.getProp());
				notifySwarmPassFail(callbackURL, buildURL);
			}

		} catch (Exception e) {
			logger.log(Level.INFO, "Unable to Notify Review", e);
		}
	}

	@Override
	public void onStarted(Run run, TaskListener listener) {
		if (run == null || run instanceof MatrixRun) {
			return;
		}
		logger.fine("ReviewNotifier: onStarted");
		Jenkins j = Jenkins.getInstanceOrNull();
		if (j == null) {
			return;
		}

		try {
			EnvVars env = run.getEnvironment(listener);
			String updateCallback = env.get(ReviewProp.SWARM_UPDATE.getProp());
			if (StringUtils.isNotEmpty(updateCallback)) {
				// onStarted() is only called once when build is stated. A scheduled task required to get message and notify update periodically
				Timer timer = new Timer();

				TimerTask task = new TimerTask() {
					@Override
					public void run() {
						if (run.isBuilding()) {
							List<P4SwarmUpdateAction> actions = run.getActions(P4SwarmUpdateAction.class);
							if (!CollectionUtils.isEmpty(actions)) {
								List<String> message = getUpdateMessage(actions);
								try {
									String buildURL = getBuildURL(run);
									notifySwarmUpdate(updateCallback, "running", message, buildURL);
								} catch (Exception e) {
									logger.log(Level.WARNING, "Unable to notify swarm update: " + e.getMessage(), e);
								}
							}
						} else {
							logger.log(Level.INFO, "Build finished: " + run.getFullDisplayName());
							timer.cancel();
						}
					}
				};
				timer.scheduleAtFixedRate(task, 2000, 10000);
			} else {
				logger.log(Level.INFO, "Skipping job onStarted because update callback url is empty.");
			}
		} catch (Exception e) {
			logger.log(Level.INFO, "onStarted Unable to Notify Review: " + e.getMessage(), e);
		}
	}

	private static List<String> getUpdateMessage(List<P4SwarmUpdateAction> action) {
		// Reverse swarm update message, swarm will accept latest 10 messages only
		List<String> messages = new ArrayList<>();
		for (int i = action.size() - 1; i >= 0; i--) {
			P4SwarmUpdateAction actionItem = action.get(i);
			messages.add(actionItem.getMessage());
		}
		return messages;
	}

	/**
	 * Post a reply.  Designed to update a Swarm test run.
	 * json POST of:  {"messages":messages_array,"url":callback,"status":status}
	 *
	 * @param callback url
	 * @param status   Swarm supported status values are running, fail, pass.  Not enforced.
	 * @param messages zero or more messages to send
	 */
	private void notifySwarmUpdate(String callback, String status, List<String> messages, String buildUrl) throws Exception {
		if (StringUtils.isEmpty(callback)) {
			return;
		}
		logger.info("ReviewNotifier set status= " + status + " to " + callback);
		JSONObject jo = new JSONObject();
		jo.put("url", buildUrl);
		jo.put("status", status);
		jo.put("messages", messages.isEmpty() ? "" : new JSONArray(messages));

		String postContent = jo.toString();
		HttpURLConnection http = postRequest(callback, postContent);
		logger.info("Response code: " + http.getResponseCode());
	}

	private void notifySwarmPassFail(String callback, String buildUrl) throws Exception {
		if (StringUtils.isEmpty(callback)) {
			return;
		}
		logger.info("ReviewNotifier to " + callback + " with url=" + buildUrl);
		String postContent = "url=" + buildUrl;
		HttpURLConnection http = postRequest(callback, postContent);
		int response = http.getResponseCode();
		logger.info("Response code: " + response);
	}

	private static HttpURLConnection postRequest(String postUrl, String postContent) throws IOException {
		URL url = new URL(postUrl);
		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.setDoInput(true);
		http.setDoOutput(true);
		http.setUseCaches(false);
		http.setRequestMethod("POST");
		http.connect();
		OutputStreamWriter writer = new OutputStreamWriter(http.getOutputStream(), StandardCharsets.UTF_8);
		writer.write(postContent);
		writer.close();
		return http;
	}

	private String getBuildURL(Run run) {
		Jenkins j = Jenkins.getInstanceOrNull();
		if (j == null) {
			logger.warning("Internal failure onCompleted:  no jenkins instance.");
			return "";
		} else {
			String rootUrl = j.getRootUrl();
			if (rootUrl == null || rootUrl.isEmpty()) {
				JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
				rootUrl = (globalConfig.getUrl() == null) ? "missing Jenkins URL in global config" : globalConfig.getUrl();
			}
			return rootUrl + run.getUrl();
		}
	}
}
