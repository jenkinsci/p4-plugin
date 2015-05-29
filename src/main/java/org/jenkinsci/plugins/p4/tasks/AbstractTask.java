package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.model.TaskListener;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.workspace.Workspace;

public abstract class AbstractTask implements Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(AbstractTask.class
			.getName());

	private P4StandardCredentials credential;
	private TaskListener listener;
	private String client;

	transient private Workspace workspace;

	public P4StandardCredentials getCredential() {
		return credential;
	}

	public void setCredential(String credential) {
		this.credential = ConnectionHelper.findCredential(credential);
	}

	public TaskListener getListener() {
		return listener;
	}

	public void setListener(TaskListener listener) {
		this.listener = listener;
	}

	public void setWorkspace(Workspace workspace) throws AbortException {
		this.workspace = workspace;
		this.client = workspace.getFullName();

		// setup the client workspace to use for the build.
		ClientHelper p4 = getConnection();

		// Check connection (might be on remote slave)
		if (!checkConnection(p4)) {
			String err = "P4: Abort, no server connection.\n";
			logger.severe(err);
			p4.log(err);
			throw new AbortException(err);
		}

		// Set the client
		try {
			p4.setClient(workspace);
			p4.log("... client: " + getClient());
		} catch (Exception e) {
			String err = "P4: Unable to setup workspace: " + e;
			logger.severe(err);
			p4.log(err);
			throw new AbortException(err);
		} finally {
			p4.disconnect();
		}
	}

	protected String getClient() {
		return client;
	}

	protected Workspace getWorkspace() {
		return workspace;
	}

	protected ClientHelper getConnection() {
		ClientHelper p4 = new ClientHelper(credential, listener, client);
		return p4;
	}

	protected boolean checkConnection(ClientHelper p4) {
		p4.log("\nP4 Task: establishing connection.");

		// test server connection
		if (!p4.isConnected()) {
			p4.log("P4: Server connection error: "
					+ getCredential().getP4port());
			return false;
		}
		p4.log("... server: " + getCredential().getP4port());

		// test node hostname
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			host = "unknown";
		}
		p4.log("... node: " + host);
		return true;
	}
}
