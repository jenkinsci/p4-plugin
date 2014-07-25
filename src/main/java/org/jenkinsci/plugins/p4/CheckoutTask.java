package org.jenkinsci.plugins.p4;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.Workspace;

public class CheckoutTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(CheckoutTask.class
			.getName());

	private final P4StandardCredentials credential;
	private final TaskListener listener;
	private final String client;

	private CheckoutStatus status;
	private int head;
	private int change;
	private int review;
	private String label;
	private List<Object> changes;
	private Populate populate;

	/**
	 * Constructor
	 * 
	 * @param config
	 *            - Server connection details
	 * @param auth
	 *            - Server login details
	 */
	public CheckoutTask(String credentialID, Workspace config,
			TaskListener listener) {
		this.credential = ConnectionHelper.findCredential(credentialID);
		this.listener = listener;
		this.client = config.getFullName();
	}

	public boolean setBuildOpts(Workspace workspace) {

		boolean success = true;
		ClientHelper p4 = new ClientHelper(credential, listener, client);

		try {
			// setup the client workspace to use for the build.
			success &= p4.setClient(workspace);
			if (!success) {
				return false;
			}

			// fetch and calculate change to sync to or review to unshelve.
			this.status = getStatus(workspace);
			this.head = p4.getClientHead();
			this.change = getChange(workspace);
			this.review = getReview(workspace);
			this.label = getLabel(workspace);

			// add changes to list for this build.
			if (label != null && !label.isEmpty()) {
				changes = new ArrayList<Object>();
				changes.add(label);
			} else {
				changes = p4.listChanges(change);
			}
			if (status == CheckoutStatus.SHELVED) {
				changes.add(review);
			}
		} catch (Exception e) {
			String err = "Unable to setup workspace: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}
		return success;
	}

	public void setPopulateOpts(Populate populate) {
		this.populate = populate;
	}

	/**
	 * Invoke sync on build node (master or remote node).
	 * 
	 * @return true if updated, false if no change.
	 */
	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {

		boolean success = true;
		ClientHelper p4 = new ClientHelper(credential, listener, client);

		try {
			// Tidy the workspace before sync/build
			success &= p4.tidyWorkspace(populate);

			// Sync workspace to label, head or specified change
			if (label != null && !label.isEmpty()) {
				success &= p4.syncFiles(label, populate);
			} else {
				success &= p4.syncFiles(change, populate);
			}

			// Unshelve review if specified
			if (status == CheckoutStatus.SHELVED) {
				success &= p4.unshelveFiles(review);
			}
		} catch (Exception e) {
			String err = "Unable to update workspace: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
			success = false;
		} finally {
			p4.disconnect();
		}
		return success;
	}

	/**
	 * Get the build status for the parameter map.
	 * 
	 * @param map
	 * @return
	 */
	private CheckoutStatus getStatus(Workspace workspace) {
		CheckoutStatus status = CheckoutStatus.HEAD;
		String value = workspace.get("status");
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
	private int getChange(Workspace workspace) {
		int change = this.head;
		String value = workspace.get("change");
		if (value != null && !value.isEmpty()) {
			try {
				change = Integer.parseInt(value);
			} catch (NumberFormatException e) {
			}
		}
		return change;
	}

	/**
	 * Get the label from the parameter map. Returns null if no label found in
	 * the map.
	 * 
	 * @param map
	 * @return
	 */
	private String getLabel(Workspace workspace) {
		String label = null;
		String value = workspace.get("label");
		if (value != null && !value.isEmpty()) {
			label = value;
		}
		return label;
	}

	/**
	 * Get the unshelve point from the parameter map.
	 * 
	 * @param map
	 * @return
	 */
	private int getReview(Workspace workspace) {
		int review = 0;
		String value = workspace.get("review");
		if (value != null && !value.isEmpty()) {
			try {
				review = Integer.parseInt(value);
			} catch (NumberFormatException e) {
			}
		}
		return review;
	}

	public List<Object> getChanges() {
		return changes;
	}

	public CheckoutStatus getStatus() {
		return status;
	}

	public int getChange() {
		return change;
	}
}
