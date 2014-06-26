package org.jenkinsci.plugins.p4_client;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4_client.client.ClientHelper;
import org.jenkinsci.plugins.p4_client.client.ConnectionHelper;
import org.jenkinsci.plugins.p4_client.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4_client.populate.Populate;
import org.jenkinsci.plugins.p4_client.workspace.Workspace;

public class CheckoutTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(CheckoutTask.class
			.getName());

	private final P4StandardCredentials credential;
	private final TaskListener listener;
	private final String client;
	private final CheckoutStatus status;

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
			TaskListener listener, Map<String, String> map) {

		this.credential = ConnectionHelper.findCredential(credentialID);
		this.listener = listener;
		this.client = config.getName();
		this.status = getStatus(map);

		try {
			ClientHelper p4 = new ClientHelper(credential, listener, client);

			// setup the client workspace to use for the build.
			p4.setClient(config);

			// fetch and calculate change to sync to or review to unshelve.
			this.head = p4.getClientHead();
			this.change = getChange(map);
			this.review = getReview(map);
			this.label = getLabel(map);

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

			p4.disconnect();
		} catch (Exception e) {
			String err = "Unable to setup workspace: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
		}

	}

	public void setPopulate(Populate populate) {
		this.populate = populate;
	}

	/**
	 * Invoke sync on build node (master or remote node).
	 * 
	 * @return true if updated, false if no change.
	 */
	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {

		logger.info("P4:CheckoutTask sync...");
		boolean success = true;

		try {
			ClientHelper p4 = new ClientHelper(credential, listener, client);

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
			p4.disconnect();
		} catch (Exception e) {
			String err = "Unable to run sync workspace: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
			return false;
		}
		return success;
	}

	/**
	 * Get the build status for the parameter map.
	 * 
	 * @param map
	 * @return
	 */
	private CheckoutStatus getStatus(Map<String, String> map) {
		CheckoutStatus status = CheckoutStatus.HEAD;
		if (map != null && map.containsKey("status")) {
			String value = map.get("status");
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
	private int getChange(Map<String, String> map) {
		int change = this.head;
		if (map != null && map.containsKey("change")) {
			String value = map.get("change");
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
	private String getLabel(Map<String, String> map) {
		String label = null;
		if (map != null && map.containsKey("label")) {
			label = map.get("label");
		}
		return label;
	}

	/**
	 * Get the unshelve point from the parameter map.
	 * 
	 * @param map
	 * @return
	 */
	private int getReview(Map<String, String> map) {
		int review = 0;
		if (map != null && map.containsKey("review")) {
			String value = map.get("review");
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
