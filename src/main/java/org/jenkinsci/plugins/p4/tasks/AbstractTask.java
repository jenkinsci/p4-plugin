package org.jenkinsci.plugins.p4.tasks;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

public abstract class AbstractTask implements Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(AbstractTask.class.getName());

	private P4BaseCredentials credential;
	private TaskListener listener;
	private String client;
	private String charset;

	transient private Workspace workspace;

	/**
	 * Implements the Perforce task to retry if necessary
	 * 
	 * @param p4
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
		this.charset = workspace.getCharset();

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

	public Workspace setEnvironment(Run<?, ?> run, Workspace wsType, FilePath buildWorkspace)
			throws IOException, InterruptedException {

		Workspace ws = (Workspace) wsType.clone();

		// Set environment
		EnvVars envVars = run.getEnvironment(listener);
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", "master"));
		ws.setExpand(envVars);

		// Set workspace root (check for parallel execution)
		String root = buildWorkspace.getRemote();
		if (root.contains("@")) {
			root = root.replace("@", "%40");
			String client = ws.getFullName();
			String name = buildWorkspace.getName();
			String[] parts = name.split("@");
			String exec = parts[1];

			// Update Workspace before cloning
			setWorkspace(ws);

			// Template workspace to .cloneN (where N is the @ number)
			String charset = ws.getCharset();
			boolean pin = ws.isPinHost();
			String template = client + ".clone" + exec;
			ws = new TemplateWorkspaceImpl(charset, pin, client, template);
			ws.setExpand(envVars);
		}
		ws.setRootPath(root);
		
		if (ws.isPinHost()) {
			String hostname = getHostName(buildWorkspace);
			ws.setHostName(hostname);
		} else {
			ws.setHostName("");
		}
		return ws;
	}
	
	public Workspace setNextChange(Workspace ws, List<Integer> changes) {
		// Set label for changes to build
		if (changes != null) {
			if (!changes.isEmpty()) {
				String label = Integer.toString(changes.get(0));
				ws.getExpand().set(ReviewProp.LABEL.toString(), label);
			}
		}
		return ws;
	}

	/**
	 * Remote execute to find hostname.
	 * 
	 * @param buildWorkspace
	 */
	private static String getHostName(FilePath buildWorkspace) {
		try {
			HostnameTask task = new HostnameTask();
			String hostname = buildWorkspace.act(task);
			return hostname;
		} catch (Exception e) {
			return "";
		}
	}

	protected String getClient() {
		return client;
	}

	protected Workspace getWorkspace() {
		return workspace;
	}

	protected ClientHelper getConnection() {
		ClientHelper p4 = new ClientHelper(credential, listener, client, charset);
		return p4;
	}

	protected boolean checkConnection(ClientHelper p4) {
		p4.log("\nP4 Task: establishing connection.");

		// test server connection
		if (!p4.isConnected()) {
			p4.log("P4: Server connection error: " + getCredential().getP4port());
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
