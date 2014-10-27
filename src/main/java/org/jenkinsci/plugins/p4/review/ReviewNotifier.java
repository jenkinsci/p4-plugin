package org.jenkinsci.plugins.p4.review;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.listeners.RunListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

@Extension
@SuppressWarnings("rawtypes")
public class ReviewNotifier extends RunListener<Run> {

	private static Logger logger = Logger.getLogger(ReviewNotifier.class
			.getName());

	private String rootUrl = Jenkins.getInstance().getRootUrl();
	private String fail;
	private String pass;

	public ReviewNotifier() {
		super(Run.class);
	}

	@Override
	public Environment setUpEnvironment(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException,
			RunnerAbortedException {

		// Ensure Urls are reset from previous build
		fail = null;
		pass = null;

		@SuppressWarnings("unchecked")
		Map<String, String> map = build.getBuildVariables();
		if (map != null) {
			if (map.containsKey(ReviewProp.FAIL.toString())) {
				fail = map.get(ReviewProp.FAIL.toString());
			}
			if (map.containsKey(ReviewProp.PASS.toString())) {
				pass = map.get(ReviewProp.PASS.toString());
			}
		}

		return new Environment() {
		};
	}

	@Override
	public void onFinalized(Run r) {
		String url = fail;

		Result result = r.getResult();
		if (result.equals(Result.SUCCESS)) {
			url = pass;
		}

		try {
			if (url != null && !url.isEmpty()) {
				if (rootUrl == null) {
					postURL(url, null);
				} else {
					String name = r.getParent().getName();
					int id = r.getNumber();
					String path = "job/" + name + "/" + id + "/";
					postURL(url, rootUrl + path);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;
	}

	private void postURL(String url, String buildUrl) throws Exception {
		HttpClient client = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(url);
		if (buildUrl != null) {
			ArrayList<NameValuePair> postParameters;
			postParameters = new ArrayList<NameValuePair>();
			postParameters.add(new BasicNameValuePair("url", buildUrl));
			post.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
		}
		HttpResponse response = client.execute(post);
		logger.info("Response code: " + response);
	}
}
