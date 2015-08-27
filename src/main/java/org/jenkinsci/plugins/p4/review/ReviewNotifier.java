package org.jenkinsci.plugins.p4.review;

import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.MatrixRun;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.model.listeners.RunListener;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

@Extension
public class ReviewNotifier extends RunListener<Run> {

	private static Logger logger = Logger.getLogger(ReviewNotifier.class
			.getName());

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
			String url = (result.equals(Result.SUCCESS)) ? pass : fail;

			if (url != null && !url.isEmpty()) {
				String rootUrl = Jenkins.getInstance().getRootUrl();
				if (rootUrl == null) {
					postURL(url, null);
				} else {
					String path = run.getUrl();
					postURL(url, rootUrl + path);
				}
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

		OutputStreamWriter writer = new OutputStreamWriter(http.getOutputStream());
		writer.write("url=" + buildUrl);
		writer.flush();
		writer.close();
		
		int response = http.getResponseCode();
		logger.info("Response code: " + response);
	}
}
