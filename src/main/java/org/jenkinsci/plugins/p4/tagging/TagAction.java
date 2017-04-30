package org.jenkinsci.plugins.p4.tagging;

import com.perforce.p4java.impl.generic.core.Label;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.AbstractScmTagAction;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.tasks.TaggingTask;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagAction extends AbstractScmTagAction {

	private static Logger logger = Logger.getLogger(TagAction.class.getName());

	private String tag;
	private List<String> tags = new ArrayList<String>();

	private P4Revision buildChange;

	private final String credential;
	private final String p4port;
	private final String p4user;
	private final String p4ticket;

	// Set when workspace is defined
	private Workspace workspace;
	private String client;
	private String syncID;
	private String charset;

	public TagAction(Run<?, ?> run, String credential) throws IOException, InterruptedException {
		super(run);

		P4BaseCredentials auth = ConnectionHelper.findCredential(credential, run);
		this.credential = credential;
		this.p4port = auth.getP4port();
		this.p4user = auth.getUsername();

		ConnectionHelper p4 = new ConnectionHelper(auth, null);
		this.p4ticket = p4.getTicket();
		p4.disconnect();
	}

	public String getIconFileName() {
		if (!getACL().hasPermission(PerforceScm.TAG))
			return null;
		return "/plugin/p4/icons/label.gif";
	}

	public String getDisplayName() {
		if (isTagged())
			return "Perforce Label";
		else
			return "Label This Build";
	}

	@Override
	public boolean isTagged() {
		return tags != null && !tags.isEmpty();
	}

	public void doSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception, ServletException {

		getACL().checkPermission(PerforceScm.TAG);

		String description = req.getParameter("desc");
		String name = req.getParameter("name");

		TaskListener listener = new LogTaskListener(logger, Level.INFO);

		labelBuild(listener, name, description, null);

		rsp.sendRedirect(".");
	}

	public void labelBuild(TaskListener listener, String name, String description, final FilePath nodeWorkspace)
			throws Exception {
		// Expand label name and description
		EnvVars env = getRun().getEnvironment(listener);
		Expand expand = new Expand(env);
		name = expand.format(name, false);
		description = expand.format(description, false);

		TaggingTask task = new TaggingTask(name, description);
		task.setListener(listener);
		task.setCredential(credential, getRun().getParent());
		task.setWorkspace(workspace);
		task.setBuildChange(buildChange);

		FilePath buildWorkspace = nodeWorkspace;
		if (nodeWorkspace == null) {
			buildWorkspace = build.getWorkspace();
		}
		if (buildWorkspace == null) {
			logger.warning("FilePath is null!");
			return;
		}

		// Invoke the Label Task
		Boolean ok = buildWorkspace.act(task);

		// save label
		if (ok && !tags.contains(name)) {
			tags.add(name);
			getRun().save();
		}
	}

	public void setBuildChange(P4Revision buildChange) {
		this.buildChange = buildChange;
	}

	public P4Revision getBuildChange() {
		return buildChange;
	}

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public void setWorkspace(Workspace workspace) {
		this.workspace = workspace;
		this.client = workspace.getFullName();
		this.syncID = workspace.getSyncID();
		this.charset = workspace.getCharset();
	}

	public String getPort() {
		return p4port;
	}

	public String getClient() {
		return client;
	}

	public String getSyncID() {
		return syncID;
	}

	public String getUser() {
		return p4user;
	}

	public String getTicket() {
		return p4ticket;
	}

	public String getTag() {
		return tag;
	}

	public List<String> getTags() {
		return tags;
	}

	/**
	 * Method used by Jelly code to show Label information (do not remove)
	 *
	 * @param tag Label name
	 * @return Perforce Label object
	 */
	public Label getLabel(String tag) {
		ClientHelper p4 = new ClientHelper(ClientHelper.findCredential(credential, getRun()), null, client, charset);
		try {
			Label label = p4.getLabel(tag);
			return label;
		} catch (Exception e) {
			logger.warning("Unable to get label from tag: " + tag);
		} finally {
			p4.disconnect();
		}
		return null;
	}

	/**
	 * Change reporting...
	 *
	 * @param run      The current build
	 * @param listener Listener for logging
	 * @param syncID   Changelist Sync ID
	 * @return Perforce change
	 */
	public static P4Revision getLastChange(Run<?, ?> run, TaskListener listener, String syncID) {
		P4Revision last = null;

		List<TagAction> actions = lastActions(run);
		if (actions == null || syncID == null || syncID.isEmpty()) {
			listener.getLogger().println("No previous build found...");
			return last;
		}

		// look for action matching view
		for (TagAction action : actions) {           //JENKINS-43877
			if (syncID.equals(action.getSyncID()) || (action.getSyncID() != null && action.getSyncID().contains(syncID))) {
				last = action.getBuildChange();
				listener.getLogger().println("Found last change " + last.toString() + " on syncID " + syncID);
			}
		}

		return last;
	}

	/**
	 * Find the last action; use this for environment variable as the last action has the latest values.
	 *
	 * @param run      The current build
	 * @return Action
	 */
	public static TagAction getLastAction(Run<?, ?> run) {
		List<TagAction> actions = lastActions(run);
		if (actions == null) {
			return null;
		}

		// #Review 21165
		TagAction last = actions.get(actions.size() - 1);
		return last;
	}

	private static List<TagAction> lastActions(Run<?, ?> run) {
		// get last run, if none then build now.
		if (run == null) {
			return null;
		}

		// get last action, if no previous action then build now.
		List<TagAction> actions = run.getActions(TagAction.class);
		if (actions.isEmpty()) {
			return null;
		}

		return actions;
	}
}
