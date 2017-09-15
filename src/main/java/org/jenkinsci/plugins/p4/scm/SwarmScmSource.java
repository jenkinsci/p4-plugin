package org.jenkinsci.plugins.p4.scm;

import com.google.gson.Gson;
import com.perforce.p4java.exception.P4JavaException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.util.NonLocalizable;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.scm.swarm.P4Path;
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
	public SwarmScmSource(String id, String credential, String project, String charset, String format) throws MalformedURLException, P4JavaException {
		super(id, credential);
		
		this.project = project;
		setCharset(charset);
		setFormat(format);

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, null);
		this.url = p4.getSwarm();
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
	public List<P4Head> getTags(@NonNull TaskListener listener) throws Exception {

		List<P4Head> list = new ArrayList<>();

		List<SwarmReviewsAPI.Reviews> reviews = getActiveReviews(project, listener);
		for (SwarmReviewsAPI.Reviews review : reviews) {
			String reviewID = String.valueOf(review.getId());

			List<String> branches = getBranchesInReview(reviewID, project, listener);
			for (String branch : branches) {
				List<P4Path> paths = getPathsInBranch(branch, project, listener);
				String trgName = branch + "-" + reviewID;
				P4Head target = new P4Head(trgName, paths);
				P4ChangeRequestSCMHead tag = new P4ChangeRequestSCMHead(trgName, reviewID, paths, target);
				list.add(tag);
			}
		}

		return list;
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<P4Head> list = new ArrayList<>();

		List<SwarmProjectAPI.Branch> branches = getBranchesInProject(project, listener);
		for(SwarmProjectAPI.Branch branch : branches) {
			List<P4Path> paths = branch.getPaths();
			P4Head head = new P4Head(branch.getId(), paths);
			list.add(head);
		}

		return list;
	}

	@Override
	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		if(head instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead changeRequest = (P4ChangeRequestSCMHead) head;
			String review = changeRequest.getReview();
			long change = getLastChangeInReview(review, listener);

			P4Revision revision = new P4Revision(head, new P4ChangeRef(change));
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

	private List<SwarmReviewsAPI.Reviews> getActiveReviews(String project, TaskListener listener) throws Exception {
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

		String apiString = apiGET(urlApi, listener);

		Gson gson = new Gson();
		SwarmReviewsAPI api = gson.fromJson(apiString.toString(), SwarmReviewsAPI.class);
		return api.getReviews();
	}

	private SwarmReviewAPI getSwarmReview(String review, TaskListener listener) throws Exception {
		// https://swarm.perforce.com/api/v6/reviews/1520872?fields=projects,changes,commits
		String fields = "projects,changes,commits";

		StringBuffer params = new StringBuffer("?");
		params.append("fields=" + fields);

		URL urlApi = new URL(url + "/api/v6/reviews/" + review + params);

		String apiString = apiGET(urlApi, listener);

		Gson gson = new Gson();
		SwarmReviewAPI api = gson.fromJson(apiString.toString(), SwarmReviewAPI.class);

		return api;
	}

	private List<String> getBranchesInReview(String review, String project, TaskListener listener) throws Exception {
		SwarmReviewAPI api = getSwarmReview(review, listener);

		HashMap<String, List<String>> projects = api.getReview().getProjects();
		List<String> branches = projects.get(project);

		return branches;
	}

	private long getLastChangeInReview(String review, TaskListener listener) throws Exception {
		SwarmReviewAPI api = getSwarmReview(review, listener);

		List<Long> changes = api.getReview().getChanges();

		long lastChange = 0;
		for (Long change : changes) {
			if (change > lastChange) {
				lastChange = change;
			}
		}
		return lastChange;
	}

	private List<P4Path> getPathsInBranch(String id, String project, TaskListener listener) throws Exception {

		List<SwarmProjectAPI.Branch> branches = getBranchesInProject(project, listener);

		for (SwarmProjectAPI.Branch branch : branches) {
			if (id.equals(branch.getId())) {
				List<P4Path> paths = branch.getPaths();
				return paths;
			}
		}

		return new ArrayList<>();
	}

	private List<SwarmProjectAPI.Branch> getBranchesInProject(String project, TaskListener listener) throws Exception {
		// https://swarm.perforce.com/api/v6/projects/swarm?fields=branches
		String fields = "branches";

		StringBuffer params = new StringBuffer("?");
		params.append("fields=" + fields);

		URL urlApi = new URL(url + "/api/v6/projects/" + project + params);

		String apiString = apiGET(urlApi, listener);

		Gson gson = new Gson();
		SwarmProjectAPI api = gson.fromJson(apiString.toString(), SwarmProjectAPI.class);

		List<SwarmProjectAPI.Branch> branches = api.getProject().getBranches();
		return branches;
	}

	private String apiGET(URL url, TaskListener listener) throws IOException {

		String auth = getBasicAuth(listener);

		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.setDoInput(true);
		http.setDoOutput(true);
		http.setUseCaches(false);
		http.setRequestMethod("GET");
		http.setRequestProperty("Authorization", "Basic " + auth);
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

	private String getBasicAuth(TaskListener listener) {
		try (ClientHelper p4 = new ClientHelper(getOwner(), credential, listener, scmSourceClient, getCharset())) {
			String user = p4.getUser();
			String ticket = p4.getTicket();

			byte[] message = (user + ":" + ticket).getBytes("UTF-8");
			String encoded = javax.xml.bind.DatatypeConverter.printBase64Binary(message);
			return encoded;
		} catch (Exception e) {
			return null;
		}
	}

	@Extension
	@Symbol("multiSwarm")
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Helix Swarm";
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
