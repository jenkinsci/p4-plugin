package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jenkins.security.Roles;

import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import com.perforce.p4java.impl.generic.core.Label;

public class CheckoutTask extends AbstractTask implements
		FileCallable<Boolean>, RoleSensitive, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(CheckoutTask.class
			.getName());

	private final Populate populate;

	private CheckoutStatus status;
	private int head;
	private Object buildChange;
	private int review;

	/**
	 * Constructor
	 *
	 * @param populate
	 */
	public CheckoutTask(Populate populate) {
		this.populate = populate;
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

	public void initialise() throws AbortException {
		ClientHelper p4 = getConnection();
		try {
			// fetch and calculate change to sync to or review to unshelve.
			status = getStatus(getWorkspace());
			head = p4.getClientHead();
			review = getReview(getWorkspace());
			buildChange = getBuildChange(getWorkspace());

			// try to get change-number if automatic label
			if (buildChange instanceof String) {
				String label = (String) buildChange;
				if (p4.isLabel(label)) {
					Label labelSpec = p4.getLabel(label);
					String revSpec = labelSpec.getRevisionSpec();
					if (revSpec != null && !revSpec.isEmpty()
							&& revSpec.startsWith("@")) {
						try {
							int change = Integer.parseInt(revSpec.substring(1));
							buildChange = change;
						} catch (NumberFormatException e) {
							// leave buildChange as is
						}
					}
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
	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {

		ClientHelper p4 = getConnection();
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}

			// Tidy the workspace before sync/build
			p4.tidyWorkspace(populate);

			// Sync workspace to label, head or specified change
			p4.syncFiles(buildChange, populate);

			// Unshelve review if specified
			if (status == CheckoutStatus.SHELVED) {
				p4.unshelveFiles(review);
			}
		} catch (Exception e) {
			String msg = "Unable to update workspace: " + e;
			logger.warning(msg);
			throw new AbortException(msg);
		} finally {
			p4.disconnect();
		}
		return true;
	}

	/**
	 * Get the build status for the parameter map.
	 *
	 * @param map
	 * @return
	 */
	private CheckoutStatus getStatus(Workspace workspace) {
		CheckoutStatus status = CheckoutStatus.HEAD;
		String value = workspace.get(ReviewProp.STATUS.toString());
		if (value != null && !value.isEmpty()) {
			status = CheckoutStatus.parse(value);
		}
		return status;
	}

	/**
	 * Get the sync point from the parameter map. Returns the head if no change
	 * found in the map.
	 *
	 * @param map
	 * @return
	 */
	private Object getBuildChange(Workspace workspace) {
		// Use head as the default
		Object build = this.head;

		// if a pinned change/label is specified the update
		String populateLabel = populate.getPin();
		if (populateLabel != null && !populateLabel.isEmpty()) {
			// Expand label with environment vars if one was defined
			populateLabel = getWorkspace().expand(populateLabel, false);
			try {
				// if build is a change-number passed as a label
				build = Integer.parseInt(populateLabel);
			} catch (NumberFormatException e) {
				build = populateLabel;
			}
		}

		// if change is specified then update
		String change = workspace.get(ReviewProp.CHANGE.toString());
		if (change != null && !change.isEmpty()) {
			try {
				build = Integer.parseInt(change);
			} catch (NumberFormatException e) {
			}
		}

		// if label is specified via label in populate config then update
		String populateLabel = populate.getPin();
		if (populateLabel != null && !populateLabel.isEmpty()) {
			// Expand label with environment vars if one was defined
			populateLabel = getWorkspace().expand(populateLabel, false);
			try {
				// if build is a change-number passed as a label
				build = Integer.parseInt(populateLabel);
			} catch (NumberFormatException e) {
				build = populateLabel;
			}
		}

		// if label is specified then update
		String label = workspace.get(ReviewProp.LABEL.toString());
		if (label != null && !label.isEmpty()) {
			try {
				// if build is a change-number passed as a label
				build = Integer.parseInt(label);
			} catch (NumberFormatException e) {
				build = label;
			}
		}

		return build;
	}

	/**
	 * Get the unshelve point from the parameter map.
	 *
	 * @param map
	 * @return
	 */
	private int getReview(Workspace workspace) {
		int review = 0;
		String value = workspace.get(ReviewProp.REVIEW.toString());
		if (value != null && !value.isEmpty()) {
			try {
				review = Integer.parseInt(value);
			} catch (NumberFormatException e) {
			}
		}
		return review;
	}

	public List<Integer> getChanges(Object last) {

		List<Integer> changes = new ArrayList<Integer>();

		// Add changes to this build.
		ClientHelper p4 = new ClientHelper(getCredential(), getListener(),
				getClient());
		try {
			changes = p4.listChanges(last, buildChange);
		} catch (Exception e) {
			String err = "Unable to get changes: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		// Include shelf if a review
		if (status == CheckoutStatus.SHELVED) {
			changes.add(review);
		}

		return changes;
	}

	public List<Object> getChangesFull(Object last) {

		List<Object> changesFull = new ArrayList<Object>();
		List<Integer> changes = new ArrayList<Integer>();

		// Add changes to this build.
		ClientHelper p4 = new ClientHelper(getCredential(), getListener(),
				getClient());
		try {
			if (status == CheckoutStatus.SHELVED) {
				P4ChangeEntry cl = new P4ChangeEntry();
				cl.setChange(p4, review);
				changesFull.add(cl);
			}

			changes = p4.listChanges(last, buildChange);
			for (Integer change : changes) {
				P4ChangeEntry cl = new P4ChangeEntry();
				cl.setChange(p4, change);
				changesFull.add(cl);
			}

		} catch (Exception e) {
			String err = "Unable to get changes: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		return changesFull;
	}

	public CheckoutStatus getStatus() {
		return status;
	}

	// Returns the number of the build change not the review change
	public Object getSyncChange() {
		return buildChange;
	}

	public Object getBuildChange() {
		if (status == CheckoutStatus.SHELVED) {
			return review;
		}
		return buildChange;
	}

	public void setBuildChange(Object parentChange) {
		buildChange = parentChange;
	}

	public int getReview() {
		return review;
	}
}
