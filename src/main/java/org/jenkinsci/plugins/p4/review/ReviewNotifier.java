package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.MatrixRun;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

@Extension
public class ReviewNotifier extends RunListener<Run> {

	private static Logger logger = Logger.getLogger(ReviewNotifier.class.getName());

	@Override
	public void onCompleted(Run run, TaskListener listener) {

		if (run instanceof MatrixRun) {
			return;
		}

		try {
			EnvVars env = run.getEnvironment(listener);
			String fail = env.get(ReviewProp.FAIL.getProp());
			String pass = env.get(ReviewProp.PASS.getProp());

			Result result = run.getResult();
			if (result == null) {
				logger.warning("Result is null!");
				return;
			}

			Jenkins j = Jenkins.getInstance();
			if (j == null) {
				logger.warning("Jenkins instance is null!");
				return;
			}
			
			// only process valid URLs (this gets triggered for non Reviews too)
			String url = (result.equals(Result.SUCCESS)) ? pass : fail;
			if (url != null && !url.isEmpty()) {
				String rootUrl = j.getRootUrl();
				if (rootUrl == null) {
					JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
					rootUrl = (globalConfig.getUrl() == null) ? "unset" : globalConfig.getUrl();
				}
				String path = run.getUrl();
				postURL(url, rootUrl + path);
			} 
			
		} catch (Exception e) {
			logger.warning("Unable to Notify Review");
			e.printStackTrace();
		}
		return;
	}

	private void postURL(String postUrl, String buildUrl) throws Exception {
		logger.info("ReviewNotifier: " + postUrl + " url=" + buildUrl);

		URL url = new URL(postUrl);

		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.setDoInput(true);
		http.setDoOutput(true);
		http.setUseCaches(false);
		http.setRequestMethod("POST");
		http.connect();

		OutputStreamWriter writer = new OutputStreamWriter(http.getOutputStream(), "UTF-8");
		writer.write("url=" + buildUrl);
		writer.flush();
		writer.close();

		int response = http.getResponseCode();
		logger.info("Response code: " + response);
	}
}
