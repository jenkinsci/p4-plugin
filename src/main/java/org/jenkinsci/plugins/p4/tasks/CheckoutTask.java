package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.impl.generic.core.Label;
import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4LabelRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CheckoutTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(CheckoutTask.class.getName());

	private final Populate populate;

	private CheckoutStatus status;
	private int head;
	private List<P4Ref> builds;
	private int review;

	/**
	 * Constructor
	 *
	 * @param populate Populate options
	 */
	public CheckoutTask(Populate populate) {
		this.populate = populate;
	}

	public void initialise() throws AbortException {
		ClientHelper p4 = getConnection();
		builds = new ArrayList<>();

		try {
			// fetch and calculate change to sync to or review to unshelve.
			status = getStatus(getWorkspace());
			head = p4.getClientHead();
			review = getReview(getWorkspace());
			P4Ref buildChange = getBuildChange(getWorkspace());

			// try to get change-number if automatic label
			if (buildChange.isLabel()) {
				String label = buildChange.toString();
				if (p4.isLabel(label)) {
					Label labelSpec = p4.getLabel(label);
					String revSpec = labelSpec.getRevisionSpec();
					if (revSpec != null && !revSpec.isEmpty() && revSpec.startsWith("@")) {
						try {
							int change = Integer.parseInt(revSpec.substring(1));
							// if change is bigger than head, use head
							if (change > head) {
								buildChange = new P4ChangeRef(head);
							} else {
								buildChange = new P4ChangeRef(change);
							}
						} catch (NumberFormatException e) {
							// leave buildChange as is
						}
					} else {
						String warn = "P4: Warning label is static and cannot be used with polling!";
						logger.warning(warn);
						p4.log(warn);
					}
				}
				if (p4.isCounter(label)) {
					try {
						String counter = p4.getCounter(label);
						// if a change number, add change...
						int change = Integer.parseInt(counter);
						// if change is bigger than head, use head
						if (change > head) {
							buildChange = new P4ChangeRef(head);
						} else {
							buildChange = new P4ChangeRef(change);
						}
					} catch (NumberFormatException n) {
						// no change number in counter
					}
				}
			} else {
				// if change is bigger than head, use head
				if (buildChange.getChange() > head) {
					buildChange = new P4ChangeRef(head);
				}
			}

			// add buildChange to list of changes to builds
			builds.add(buildChange);

			// Initialise Graph commit changes
			if (p4.checkVersion(20171)) {
				List<IRepo> repos = p4.listRepos();
				for (IRepo repo : repos) {
					P4Ref graphHead = p4.getGraphHead(repo.getName());
					// add graphHead to list of commits to builds
					builds.add(graphHead);
				}
			}
		} catch (Exception e) {
			String err = "P4: Unable to initialise CheckoutTask: " + e;
			logger.severe(err);
			p4.log(err);
			throw new AbortException(err);
		} finally {
			p4.disconnect();
		}
	}

	/**
	 * Invoke sync on build node (master or remote node).
	 *
	 * @return true if updated, false if no change.
	 */
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		// Tidy the workspace before sync/build
		p4.tidyWorkspace(populate);

		// Sync workspace to label, head or specified change for each repo to build
		for (P4Ref build : builds) {
			p4.syncFiles(build, populate);
		}

		// Unshelve review if specified
		if (status == CheckoutStatus.SHELVED) {
			p4.unshelveFiles(review);

			if(populate instanceof AutoCleanImpl) {
				AutoCleanImpl auto = (AutoCleanImpl) populate;
				if(auto.isTidy()) {
					p4.revertAllFiles(true);
				}
			} else {
				if(!populate.isHave()) {
					p4.revertAllFiles(true);
				}
			}
		}
		return true;
	}

	/**
	 * Get the build status for the parameter map.
	 *
	 * @param workspace
	 */
	private CheckoutStatus getStatus(Workspace workspace) {
		CheckoutStatus status = CheckoutStatus.HEAD;
		String value = workspace.getExpand().get(ReviewProp.STATUS.toString());
		if (value != null && !value.isEmpty()) {
			status = CheckoutStatus.parse(value);
		}
		return status;
	}

	/**
	 * Get the sync point from the parameter map. Returns the head if no change
	 * found in the map.
	 *
	 * @param workspace
	 */
	private P4Ref getBuildChange(Workspace workspace) {
		// Use head as the default
		P4Ref build = new P4ChangeRef(this.head);

		// Get Environment parameters from expand
		Expand expand = workspace.getExpand();

		// if a pinned change/label is specified the update
		String populateLabel = populate.getPin();
		if (populateLabel != null && !populateLabel.isEmpty()) {
			// Expand label with environment vars if one was defined
			String expandedPopulateLabel = expand.format(populateLabel, false);
			if (!expandedPopulateLabel.isEmpty()) {
				try {
					// if build is a change-number passed as a label
					int change = Integer.parseInt(expandedPopulateLabel);
					build = new P4ChangeRef(change);
					logger.info("getBuildChange:pinned:change:" + change);
				} catch (NumberFormatException e) {
					build = new P4LabelRef(expandedPopulateLabel);
					logger.info("getBuildChange:pinned:label:" + expandedPopulateLabel);
				}
			}
		}

		// if change is specified then update
		String cngStr = expand.get(ReviewProp.CHANGE.toString());
		if (cngStr != null && !cngStr.isEmpty()) {
			try {
				int change = Integer.parseInt(cngStr);
				build = new P4ChangeRef(change);
				logger.info("getBuildChange:ReviewProp:CHANGE:" + change);
			} catch (NumberFormatException e) {
			}
		}

		// if label is specified then update
		String lblStr = expand.get(ReviewProp.LABEL.toString());
		if (lblStr != null && !lblStr.isEmpty()) {
			try {
				// if build is a change-number passed as a label
				int change = Integer.parseInt(lblStr);
				build = new P4ChangeRef(change);
				logger.info("getBuildChange:ReviewProp:LABEL:" + change);
			} catch (NumberFormatException e) {
				build = new P4LabelRef(lblStr);
				logger.info("getBuildChange:ReviewProp:LABEL:" + lblStr);
			}
		}

		logger.info("getBuildChange:return:" + build);
		return build;
	}

	/**
	 * Get the unshelve point from the parameter map.
	 *
	 * @param workspace
	 */
	private int getReview(Workspace workspace) {
		int review = 0;
		Expand expand = workspace.getExpand();
		String value = expand.get(ReviewProp.REVIEW.toString());
		if (value != null && !value.isEmpty()) {
			try {
				review = Integer.parseInt(value);
			} catch (NumberFormatException e) {
			}
		}
		return review;
	}

	public List<P4ChangeEntry> getChangesFull(List<P4Ref> lastRefs) {

		List<P4ChangeEntry> changesFull = new ArrayList<P4ChangeEntry>();

		// Add changes to this build.
		ClientHelper p4 = getConnection();
		try {
			if (status == CheckoutStatus.SHELVED) {
				P4ChangeEntry cl = new P4ChangeEntry();
				IChangelistSummary pending = p4.getChange(review);
				cl.setChange(p4, pending);
				changesFull.add(cl);
			}

			// add all changes to list
			for(P4Ref build : builds) {
				if(build.isCommit()) {
					// add graph commits to list
					List<P4Ref> commits = p4.listCommits(lastRefs, build);
					for (P4Ref ref : commits) {
						P4ChangeEntry cl = ref.getChangeEntry(p4);
						changesFull.add(cl);
					}
				} else {
					// add classic changes
					List<P4Ref> changes = p4.listChanges(lastRefs, build);
					for (P4Ref change : changes) {
						P4ChangeEntry cl = change.getChangeEntry(p4);
						changesFull.add(cl);
					}
				}
			}
		} catch (Exception e) {
			String err = "Unable to get full changes: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		return changesFull;
	}

	public P4ChangeEntry getCurrentChange() {
		P4ChangeEntry cl = new P4ChangeEntry();
		P4Ref current = getBuildChange();

		ClientHelper p4 = getConnection();
		try {
			cl = current.getChangeEntry(p4);
		} catch (Exception e) {
			String err = "Unable to get current change: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		return cl;
	}

	public CheckoutStatus getStatus() {
		return status;
	}

	// Returns the build changes not the review change
	public List<P4Ref> getSyncChange() {
		return builds;
	}

	public P4Ref getBuildChange() {
		if (status == CheckoutStatus.SHELVED) {
			return new P4ChangeRef(review);
		}
		for(P4Ref build : builds) {
			if(!build.isCommit()) {
				return build;
			}
		}
		return null;
	}

	public void setBuildChange(P4Ref parentChange) {
		builds = new ArrayList<>();
		builds.add(parentChange);
	}

	public void setIncrementalChanges(List<P4Ref> changes) {
		if (changes != null && !changes.isEmpty()) {
			P4Ref lowest = changes.get(changes.size() - 1);
			builds = new ArrayList<>();
			builds.add(lowest);
		}
	}

	public int getReview() {
		return review;
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}
}
