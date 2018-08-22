package org.jenkinsci.plugins.p4.scm;

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
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmProjectAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewsAPI;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SwarmSCMSource extends AbstractP4SCMSource {

	private String project;

	transient private SwarmHelper swarm;

	@DataBoundConstructor
	public SwarmSCMSource(String credential, String charset, String format) throws Exception {
		super(credential);

		setCharset(charset);
		setFormat(format);
	}

	@DataBoundSetter
	public void setProject(String project) {
		this.project = project;
	}

	public String getProject() {
		return project;
	}

	public SwarmHelper getSwarm() throws Exception {
		if (swarm == null) {
			try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, null)) {
				this.swarm = new SwarmHelper(p4, "4");
			}
		}
		return swarm;
	}

	@Override
	public P4Browser getBrowser() {
		try {
			return new SwarmBrowser(getSwarm().getBaseUrl());
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public List<P4SCMHead> getTags(@NonNull TaskListener listener) throws Exception {

		List<P4SCMHead> list = new ArrayList<>();

		List<SwarmReviewsAPI.Reviews> reviews = getSwarm().getActiveReviews(project);
		for (SwarmReviewsAPI.Reviews review : reviews) {
			String reviewID = String.valueOf(review.getId());

			List<String> branches = getBranchesInReview(reviewID, project);
			for (String branch : branches) {
				// Get first Swarm path; it MUST include the Jenkinsfile
				P4SwarmPath swarmPath = getPathsInBranch(branch, project);

				String trgName = branch + "-" + reviewID;
				P4SCMHead target = new P4SCMHead(trgName, swarmPath);
				P4ChangeRequestSCMHead tag = new P4ChangeRequestSCMHead(trgName, reviewID, swarmPath, target);
				list.add(tag);
			}
		}

		return list;
	}

	@Override
	public List<P4SCMHead> getHeads(@NonNull TaskListener listener) throws Exception {

		List<P4SCMHead> list = new ArrayList<>();

		List<SwarmProjectAPI.Branch> branches = getSwarm().getBranchesInProject(project);
		for (SwarmProjectAPI.Branch branch : branches) {
			// Get first Swarm path; it MUST include the Jenkinsfile
			P4SwarmPath swarmPath = branch.getPath();

			P4SCMHead head = new P4SCMHead(branch.getId(), swarmPath);
			list.add(head);
		}

		return list;
	}

	@Override
	public P4SCMRevision getRevision(P4SCMHead head, TaskListener listener) throws Exception {
		if (head instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead changeRequest = (P4ChangeRequestSCMHead) head;
			String review = changeRequest.getReview();
			long change = getLastChangeInReview(review);

			P4SCMRevision revision = new P4SCMRevision(head, new P4ChangeRef(change));
			return revision;
		}
		return super.getRevision(head, listener);
	}

	@Override
	public PerforceScm build(@NonNull SCMHead head, SCMRevision revision) {
		PerforceScm scm = super.build(head, revision);
		if (head instanceof P4ChangeRequestSCMHead) {
			P4Review review = new P4Review(head.getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
		}
		return scm;
	}

	@Override
	public Workspace getWorkspace(P4Path path) {
		if (path == null || !(path instanceof P4SwarmPath)) {
			throw new IllegalArgumentException("missing Swarm path");
		}

		P4SwarmPath swarmPath = (P4SwarmPath) path;

		String client = getFormat();
		String jenkinsPath = path.getPath() + "/" + getScriptPathOrDefault();
		String jenkinsView = ViewMapHelper.getClientView(jenkinsPath, client);
		String mappingsView = ViewMapHelper.getClientView(swarmPath.getMappings(), client);
		String view = jenkinsView + "\n" + mappingsView;

		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
		return true;
	}

	private List<String> getBranchesInReview(String review, String project) throws Exception {
		SwarmReviewAPI api = getSwarm().getSwarmReview(review);

		HashMap<String, List<String>> projects = api.getReview().getProjects();
		List<String> branches = projects.get(project);

		return branches;
	}

	private long getLastChangeInReview(String review) throws Exception {
		SwarmReviewAPI api = getSwarm().getSwarmReview(review);

		List<Long> changes = api.getReview().getChanges();

		long lastChange = 0;
		for (Long change : changes) {
			if (change > lastChange) {
				lastChange = change;
			}
		}
		return lastChange;
	}

	private P4SwarmPath getPathsInBranch(String id, String project) throws Exception {

		List<SwarmProjectAPI.Branch> branches = getSwarm().getBranchesInProject(project);

		for (SwarmProjectAPI.Branch branch : branches) {
			if (id.equals(branch.getId())) {
				P4SwarmPath swarmPath = branch.getPath();
				return swarmPath;
			}
		}
		return null;
	}

	@Extension
	@Symbol("multiSwarm")
	public static final class DescriptorImpl extends P4SCMSourceDescriptor {

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
