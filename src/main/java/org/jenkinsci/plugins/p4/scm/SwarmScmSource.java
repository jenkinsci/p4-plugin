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
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmProjectAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewsAPI;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SwarmScmSource extends AbstractP4ScmSource {

	private final String project;

	transient private SwarmHelper swarm;

	@DataBoundConstructor
	public SwarmScmSource(String id, String credential, String project, String charset, String format) throws Exception {
		super(id, credential);

		this.project = project;
		setCharset(charset);
		setFormat(format);
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
	public List<P4Head> getTags(@NonNull TaskListener listener) throws Exception {

		List<P4Head> list = new ArrayList<>();

		List<SwarmReviewsAPI.Reviews> reviews = getSwarm().getActiveReviews(project);
		for (SwarmReviewsAPI.Reviews review : reviews) {
			String reviewID = String.valueOf(review.getId());

			List<String> branches = getBranchesInReview(reviewID, project);
			for (String branch : branches) {
				List<P4Path> paths = getPathsInBranch(branch, project);
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

		List<SwarmProjectAPI.Branch> branches = getSwarm().getBranchesInProject(project);
		for (SwarmProjectAPI.Branch branch : branches) {
			List<P4Path> paths = branch.getPaths();
			P4Head head = new P4Head(branch.getId(), paths);
			list.add(head);
		}

		return list;
	}

	@Override
	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		if (head instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead changeRequest = (P4ChangeRequestSCMHead) head;
			String review = changeRequest.getReview();
			long change = getLastChangeInReview(review);

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

	private List<P4Path> getPathsInBranch(String id, String project) throws Exception {

		List<SwarmProjectAPI.Branch> branches = getSwarm().getBranchesInProject(project);

		for (SwarmProjectAPI.Branch branch : branches) {
			if (id.equals(branch.getId())) {
				List<P4Path> paths = branch.getPaths();
				return paths;
			}
		}

		return new ArrayList<>();
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
