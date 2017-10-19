package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class AbstractTask implements Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(AbstractTask.class.getName());

	private P4BaseCredentials credential;
	private TaskListener listener;
	private String client;
	private String syncID;
	private String charset;

	transient private Workspace workspace;

	/**
	 * Implements the Perforce task to retry if necessary
	 *
	 * @param p4 Perforce connection helper
	 * @return Task object
	 * @throws Exception push up stack
	 */
	public abstract Object task(ClientHelper p4) throws Exception;

	public P4BaseCredentials getCredential() {
		return credential;
	}

	@Deprecated
	public void setCredential(String credential) {
		this.credential = ConnectionHelper.findCredential(credential);
	}

	public void setCredential(String credential, Item project) {
		this.credential = ConnectionHelper.findCredential(credential, project);
	}

	public void setCredential(String credential, Run run) {
		this.credential = ConnectionHelper.findCredential(credential, run);
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
		this.syncID = workspace.getSyncID();
		this.charset = workspace.getCharset();

		// setup the client workspace to use for the build.
		ClientHelper p4 = getConnection();

		// Check connection (might be on remote slave)
//		if (!checkConnection(p4)) {
//			String err = "P4: Abort, no server connection.\n";
//			logger.severe(err);
//			p4.log(err);
//			throw new AbortException(err);
//		}

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
		String node = getNodeName(buildWorkspace);
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", node));
		ws.setExpand(envVars);

		// Set workspace root (check for parallel execution)
		String root = buildWorkspace.getRemote();
		if (root.contains("@")) {
			root = root.replace("@", "%40");
		}

		// Template workspace for parallel execution
		String name = buildWorkspace.getName();
		if (name.matches(".*@[0-9]+")) {
			ws = cloneWorkspace(ws, name, envVars);
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

	private Workspace cloneWorkspace(Workspace ws, String name, EnvVars envVars) throws AbortException {

		// Skip cloning the workspace if clone option is not set
		if(ws instanceof StaticWorkspaceImpl) {
			StaticWorkspaceImpl staticWs = (StaticWorkspaceImpl)ws;
			if(!staticWs.isClone()) {
				return ws;
			}
		}

		String[] parts = name.split("@");
		if (parts.length == 2) {
			String exec = parts[1];

			// Update Workspace before cloning
			setWorkspace(ws);

			// Template workspace to .cloneN (where N is the @ number)
			try {
				int n = Integer.parseInt(exec);
				String charset = ws.getCharset();
				boolean pin = ws.isPinHost();
				String fullName = ws.getFullName();
				String template = fullName + ".clone" + n;
				ws = new TemplateWorkspaceImpl(charset, pin, fullName, template);
				ws.setExpand(envVars);
			} catch (NumberFormatException e) {
				// do not template; e.g. 'script' keeps original name
			}
		}
		return ws;
	}

	private String getNodeName(FilePath build) {
		VirtualChannel vc = build.getChannel();
		if (vc instanceof Channel) {
			Channel channel = (Channel) vc;
			return channel.getName();
		}
		return "master";
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

	public String getClient() {
		return client;
	}

	public String getSyncID() {
		return syncID;
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
				p4.disconnect();
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
