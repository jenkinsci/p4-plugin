package org.jenkinsci.plugins.p4.scm;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.util.NonLocalizable;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.scm.swarm.SwarmProjectAPI;
import org.jenkinsci.plugins.p4.scm.swarm.SwarmReviewAPI;
import org.jenkinsci.plugins.p4.scm.swarm.SwarmReviewsAPI;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SwarmScmSource extends AbstractP4ScmSource {

	private final String url;
	private final String project;

	@DataBoundConstructor
	public SwarmScmSource(String id, String credential, String url, String project, String charset, String format) throws MalformedURLException {
		super(id, credential, charset, format);
		this.url = url;
		this.project = project;
	}

	public String getUrl() {
		return url;
	}

	public String getProject() {
		return project;
	}

	@Override
	public P4Browser getBrowser() {
		return new SwarmBrowser(url);
	}

	@Override
	public List<P4ChangeRequestSCMHead> getTags(@NonNull TaskListener listener) throws Exception {

		List<P4ChangeRequestSCMHead> list = new ArrayList<>();

		List<SwarmReviewsAPI.Reviews> reviews = getActiveReviews(project);
		for (SwarmReviewsAPI.Reviews review : reviews) {
			String reviewID = String.valueOf(review.getId());

			List<String> branches = getBranchesInReview(reviewID, project);
			for (String branch : branches) {
				List<String> paths = getPathsInBranch(branch, project);
				String trgName = branch + "-" + reviewID;
				P4Head target = new P4Head(trgName, paths, false);
				P4ChangeRequestSCMHead tag = new P4ChangeRequestSCMHead(trgName, reviewID, paths, target, false);
				list.add(tag);
			}
		}

		return list;
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<P4Head> list = new ArrayList<>();

		List<SwarmProjectAPI.Branch> branches = getBranchesInProject(project);
		for(SwarmProjectAPI.Branch branch : branches) {
			List<String> paths = branch.getPaths();
			P4Head head = new P4Head(branch.getId(), paths, false);
			list.add(head);
		}

		return list;
	}

	@Override
	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		if(head instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead changeRequest = (P4ChangeRequestSCMHead) head;
			String review = changeRequest.getReview();
			long change = getLastChangeInReview(review, project);

			P4Revision revision = new P4Revision(head, change);
			return revision;
		}
		return super.getRevision(head, listener);
	}

	@Override
	public PerforceScm build(SCMHead head, SCMRevision revision) {
		PerforceScm scm = super.build(head, revision);
		if (head instanceof P4ChangeRequestSCMHead) {
			P4Review review = new P4Review(head.getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
		}
		return scm;
	}

	protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
		return true;
	}

	private List<SwarmReviewsAPI.Reviews> getActiveReviews(String project) throws Exception {
		// https://swarm.perforce.com/api/v6/reviews?max=2&fields=id,state,changes&project=swarm
		String fields = "id,state,changes";
		String max = "10";
		String state = "state[]=needsReview&state[]=needsRevision";

		StringBuffer params = new StringBuffer("?");
		params.append("max=" + max).append("&");
		params.append("fields=" + fields).append("&");
		params.append("project=" + project).append("&");
		params.append(state);

		URL urlApi = new URL(url + "/api/v6/reviews" + params);

		String apiString = apiGET(urlApi);

		Gson gson = new Gson();
		SwarmReviewsAPI api = gson.fromJson(apiString.toString(), SwarmReviewsAPI.class);
		return api.getReviews();
	}

	private SwarmReviewAPI getSwarmReview(String review, String project) throws Exception {
		// https://swarm.perforce.com/api/v6/reviews/1520872?fields=projects,changes,commits
		String fields = "projects,changes,commits";

		StringBuffer params = new StringBuffer("?");
		params.append("fields=" + fields);

		URL urlApi = new URL(url + "/api/v6/reviews/" + review + params);

		String apiString = apiGET(urlApi);

		Gson gson = new Gson();
		SwarmReviewAPI api = gson.fromJson(apiString.toString(), SwarmReviewAPI.class);

		return api;
	}

	private List<String> getBranchesInReview(String review, String project) throws Exception {
		SwarmReviewAPI api = getSwarmReview(review, project);

		HashMap<String, List<String>> projects = api.getReview().getProjects();
		List<String> branches = projects.get(project);

		return branches;
	}

	private long getLastChangeInReview(String review, String project) throws Exception {
		SwarmReviewAPI api = getSwarmReview(review, project);

		List<Long> changes = api.getReview().getChanges();

		long lastChange = 0;
		for (Long change : changes) {
			if (change > lastChange) {
				lastChange = change;
			}
		}
		return lastChange;
	}

	private List<String> getPathsInBranch(String id, String project) throws Exception {

		List<SwarmProjectAPI.Branch> branches = getBranchesInProject(project);

		for (SwarmProjectAPI.Branch branch : branches) {
			if (id.equals(branch.getId())) {
				List<String> paths = branch.getPaths();
				return paths;
			}
		}

		return new ArrayList<String>();
	}

	private List<SwarmProjectAPI.Branch> getBranchesInProject(String project) throws Exception {
		// https://swarm.perforce.com/api/v6/projects/swarm?fields=branches
		String fields = "branches";

		StringBuffer params = new StringBuffer("?");
		params.append("fields=" + fields);

		URL urlApi = new URL(url + "/api/v6/projects/" + project + params);

		String apiString = apiGET(urlApi);

		Gson gson = new Gson();
		SwarmProjectAPI api = gson.fromJson(apiString.toString(), SwarmProjectAPI.class);

		List<SwarmProjectAPI.Branch> branches = api.getProject().getBranches();
		return branches;
	}

	private String apiGET(URL url) throws IOException {
		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.setDoInput(true);
		http.setDoOutput(true);
		http.setUseCaches(false);
		http.setRequestMethod("GET");
		http.connect();

		StringBuffer apiString = new StringBuffer();

		String inputLine;
		BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream(), "UTF-8"));
		while ((inputLine = in.readLine()) != null) {
			apiString.append(inputLine);
		}
		in.close();

		return apiString.toString();
	}

	@Extension
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Reviews";
		}

		@NonNull
		@Override
		protected SCMHeadCategory[] createCategories() {
			return new SCMHeadCategory[]{
					new UncategorizedSCMHeadCategory(new NonLocalizable("Branches")),
					new ChangeRequestSCMHeadCategory(new NonLocalizable("Reviews"))
			};
		}
	}
}
