package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.build.ExecutorHelper;
import org.jenkinsci.plugins.p4.build.NodeHelper;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4InvalidCredentialException;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class AbstractTask implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(AbstractTask.class.getName());

	private final P4BaseCredentials credential;
	private final TaskListener listener;

	private Workspace workspace;

	@Deprecated
	public AbstractTask(String credential, TaskListener listener) {
		this.credential = ConnectionHelper.findCredential(credential);
		this.listener = listener;
	}

	public AbstractTask(P4BaseCredentials credential, TaskListener listener) {
		this.credential = credential;
		this.listener = listener;
	}

	public AbstractTask(String credential, Item project, TaskListener listener) {
		this.credential = ConnectionHelper.findCredential(credential, project);
		this.listener = listener;
	}

	public AbstractTask(String credential, Run run, TaskListener listener) {
		this.credential = ConnectionHelper.findCredential(credential, run);
		this.listener = listener;
	}

	/**
	 * Set the workspace used for the task.
	 * Often AbstractTask#setEnvironment() is used to expand the variables in the workspace before set
	 *
	 * @param workspace Perforce Workspace type
	 */
	public void setWorkspace(Workspace workspace) {
		this.workspace = workspace;
	}

	/**
	 * Implements the Perforce task to retry if necessary
	 *
	 * @param p4 Perforce connection helper
	 * @return Task object
	 * @throws Exception push up stack
	 */
	public abstract Object task(ClientHelper p4) throws Exception;

	public P4BaseCredentials getCredential() throws P4InvalidCredentialException {
		if (credential == null) {
			throw new P4InvalidCredentialException();
		}
		return credential;
	}

	public TaskListener getListener() {
		return listener;
	}

	public Workspace setEnvironment(Run<?, ?> run, Workspace wsType, FilePath buildWorkspace)
			throws IOException, InterruptedException {
		return setup(run, wsType, buildWorkspace, listener);
	}

	public static Workspace setup(Run<?, ?> run, Workspace wsType, FilePath buildWorkspace, TaskListener listener)
			throws IOException, InterruptedException {

		Workspace ws = wsType.deepClone();

		// Set Node environment
		EnvVars envVars = run.getEnvironment(listener);
		String nodeName = NodeHelper.getNodeName(buildWorkspace);
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", nodeName));
		String executor = ExecutorHelper.getExecutorID(buildWorkspace, listener);
		envVars.put("EXECUTOR_NUMBER", envVars.get("EXECUTOR_NUMBER", executor));

		ws.setExpand(envVars);

		// Set workspace root (check for parallel execution)
		String root = buildWorkspace.getRemote();
		if (root.contains("@")) {
			root = root.replace("@", "%40");
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

	/**
	 * Remote execute to find hostname.
	 *
	 * @param buildWorkspace Jenkins remote path
	 * @return Hostname
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

	public String getClientName() {
		return workspace.getFullName();
	}

	public String getSyncID() {
		return workspace.getSyncID();
	}

	protected Workspace getWorkspace() {
		return workspace;
	}

	protected boolean checkConnection(ClientHelper p4) {
		p4.log("\nP4 Task: establishing connection.");

		// test server connection
		if (!p4.isConnected()) {
			p4.log("P4: Server connection error: " + p4.getPort());
			return false;
		}
		p4.log("... server: " + p4.getPort());

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
		try (ClientHelper p4 = new ClientHelper(getCredential(), listener, workspace)) {

			// Check for an abort
			if (p4.hasAborted()) {
				String msg = "P4: Previous Task Aborted!";
				logger.warning(msg);
				p4.log(msg);
				throw new AbortException(msg);
			}

			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				String msg = "P4: Unable to connect.";
				logger.warning(msg);
				p4.log(msg);
				throw new AbortException(msg);
			}

			// Run the task and retry as required
			return retryTask(p4);
		} catch (Exception e) {
			String msg = "P4: Task Exception: " + e.getMessage();
			logger.severe(msg);
			AbortException ae = new AbortException(msg);
			// AbortExceptions don't have a constructor with a cause.  We want to include the cause for debugging
			// purposes so advanced developers can print the stack trace without changing the legacy behavior of
			// returning an AbortException (which has specific implications for Jenkins).
			ae.initCause(e);
			throw ae;
		}
	}

	private Object retryTask(ClientHelper p4) throws Exception {

		int t = 0;
		Exception last = null;

		while (t <= p4.getRetry()) {
			t++;
			try {
				// Run the task
				Object result = monitorTask(p4);

				if (p4.hasAborted()) {
					String msg = "P4: Task Aborted!";
					logger.warning(msg);
					p4.log(msg);
					throw new AbortException(msg);
				}
				return result;
			} catch (Exception e) {
				last = e;
				String msg = "P4 Task: attempt: " + t;
				logger.severe(msg);
				p4.log(msg);

				// back off n^2 seconds, before retry
				try {
					TimeUnit.SECONDS.sleep(t ^ 2);
				} catch (InterruptedException e2) {
					Thread.currentThread().interrupt();
				}
			}
		}

		throw new Exception(last);
	}

	private Object monitorTask(ClientHelper p4) throws Exception {

		int tick = p4.getTick();
		if (tick == 0) {
			return task(p4);
		}

		// JENKINS-58161 create background 'tick' to avoid 30s timeout
		Thread thread = new Thread(() -> {
			while (true) {
				try {
					p4.log("...tick...");
					Thread.sleep(tick);
				} catch (InterruptedException e) {
					p4.log("...finished.");
					return;
				}
			}
		});

		thread.start();
		Object result = task(p4);
		thread.interrupt();
		return result;
	}
}
