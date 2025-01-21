package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.MatrixRun;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.Timer;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.PerforceScm;
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
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ReviewNotifier extends RunListener<Run> {

	private static final Logger logger = Logger.getLogger(ReviewNotifier.class.getName());
	/*
	 * URL parameters and data for callbacks
	 */
	private String failCallback;
	private String passCallback;
	private String updateCallback;
	private String buildUrl = "";
	private boolean isUpdateURLEnabled = true; // global config

	@Override
	public void onCompleted(Run run, TaskListener listener) {
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
			populateURLs(run, listener);

			if (isUpdateURLEnabled && StringUtils.isNotEmpty(updateCallback)) {
				String status = (result.equals(Result.SUCCESS)) ? "pass" : "fail";
				List<P4SwarmUpdateAction> actions = run.getActions(P4SwarmUpdateAction.class);
				if (!CollectionUtils.isEmpty(actions)) {
					List<String> message = getUpdateMessage(actions);
					notifySwarmUpdate(updateCallback, status, message);
				}
			} else {
				String callbackURL = (result.equals(Result.SUCCESS)) ? passCallback : failCallback;
				notifySwarmPassFail(callbackURL);
			}

		} catch (Exception e) {
			logger.log(Level.INFO, "Unable to Notify Review", e);
		}
	}

	@Override
	public void onStarted(Run run, TaskListener listener) {
		if (run instanceof MatrixRun) {
			return;
		}
		logger.fine("ReviewNotifier: onStarted");
		try {
			populateURLs(run, listener);
			if (isUpdateURLEnabled && StringUtils.isNotEmpty(updateCallback)) {
				// onStarted() is only called once when build is stated. A scheduled task required to get message and notify update periodically
				scheduleSwarmUpdateURL(run);
			} else {
				logger.log(Level.INFO, "Skipping job onStarted because use update url is disabled");
			}
		} catch (Exception e) {
			logger.log(Level.INFO, "onStarted Unable to Notify Review: " + e.getMessage(), e);
		}
	}

	private void scheduleSwarmUpdateURL(Run run) {
		if (run == null) {
			return;
		}
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (run.isBuilding()) {
					List<P4SwarmUpdateAction> actions = run.getActions(P4SwarmUpdateAction.class);
					if (!CollectionUtils.isEmpty(actions)) {
						List<String> message = getUpdateMessage(actions);
						try {
							notifySwarmUpdate(updateCallback, "running", message);
						} catch (Exception e) {
							logger.log(Level.WARNING, "Unable to Notify Review: " + e.getMessage(), e);
						}
					}
				} else {
					logger.log(Level.INFO, "Build finished: " + run.getFullDisplayName());
					this.cancel();
				}
			}
		};
		Timer.get().scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);
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
	private void notifySwarmUpdate(String callback, String status, List<String> messages) throws Exception {
		logger.info("ReviewNotifier set status=" + status + " to " + callback);
		if (StringUtils.isEmpty(callback)) {
			return;
		}
		JSONObject jo = new JSONObject();
		jo.put("url", buildUrl);
		jo.put("status", status);
		JSONArray ja = new JSONArray(messages);
		jo.put("messages", ja);

		String postContent = jo.toString();
		HttpURLConnection http = postRequest(callback, postContent);
		logger.info("Response code: " + http.getResponseCode());
	}

	private void notifySwarmPassFail(String callback) throws Exception {
		logger.info("ReviewNotifier to " + callback + " with url=" + this.buildUrl);
		if (StringUtils.isEmpty(callback)) {
			return;
		}
		String postContent = "url=" + this.buildUrl;
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

	/**
	 * Lookup parameters necessary to post back.
	 *
	 * @param run
	 * @param listener
	 * @throws Exception
	 */
	private void populateURLs(Run run, TaskListener listener) throws Exception {
		EnvVars env = run.getEnvironment(listener);
		failCallback = env.get(ReviewProp.SWARM_FAIL.getProp());
		passCallback = env.get(ReviewProp.SWARM_PASS.getProp());
		updateCallback = env.get(ReviewProp.SWARM_UPDATE.getProp());

		Jenkins j = Jenkins.getInstanceOrNull();
		if (j == null) {
			// should never be here.
			logger.warning("Internal failure onCompleted:  no jenkins instance, assuming useUpdateUrl=" + isUpdateURLEnabled);
		} else {
			String rootUrl = j.getRootUrl();
			if (rootUrl == null || rootUrl.isEmpty()) {
				JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
				rootUrl = (globalConfig.getUrl() == null) ? "missing Jenkins URL in global config" : globalConfig.getUrl();
			}
			buildUrl = rootUrl + run.getUrl();

			Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
			PerforceScm.DescriptorImpl p4scm = (PerforceScm.DescriptorImpl) scm;
			if (p4scm != null) {
				isUpdateURLEnabled = p4scm.isUseSwarmUpdateUrl();
			}
		}
	}

}
