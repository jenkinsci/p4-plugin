package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.model.TaskListener;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.workspace.Workspace;

public abstract class AbstractTask implements Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(AbstractTask.class
			.getName());

	private P4BaseCredentials credential;
	private TaskListener listener;
	private String client;

	transient private Workspace workspace;

	/**
	 * Implements the Perforce task to retry if necessary
	 * 
	 * @param p4
	 * @return
	 * @throws Exception
	 */
	public abstract Object task(ClientHelper p4) throws Exception;

	public P4BaseCredentials getCredential() {
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

	protected Object tryTask() throws AbortException {
		ClientHelper p4 = getConnection();

		if (p4.hasAborted()) {
			String msg = "P4: Previous Task Aborted!";
			logger.warning(msg);
			p4.log(msg);
			p4.disconnect();
			throw new AbortException(msg);
		}

		// Check connection (might be on remote slave)
		if (!checkConnection(p4)) {
			String msg = "\nP4 Task: Unable to connect.";
			logger.warning(msg);
			p4.log(msg);
			throw new AbortException(msg);
		}

		int trys = 0;
		int attempt = p4.getRetry();
		Exception last = null;
		while (trys <= attempt) {
			trys++;
			try {
				Object result = task(p4);
				p4.disconnect();

				if (p4.hasAborted()) {
					String msg = "P4: Task Aborted!";
					logger.warning(msg);
					p4.log(msg);
					throw new AbortException(msg);
				}

				return result;
			} catch (AbortException e) {
				throw e;
			} catch (Exception e) {
				last = e;
				String msg = "P4 Task: attempt: " + trys;
				logger.severe(msg);
				p4.log(msg);
				
				// back off n^2 seconds, before retry
				try {
					TimeUnit.SECONDS.sleep(trys ^ 2);
				} catch (InterruptedException e2) {
					Thread.currentThread().interrupt();
				}
			}
		}

		p4.disconnect();
		String msg = "P4 Task: failed: " + last;
		last.printStackTrace();
		logger.warning(msg);
		p4.log(msg);
		throw new AbortException(msg);
	}
}
