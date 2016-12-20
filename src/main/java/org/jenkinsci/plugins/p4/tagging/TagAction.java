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

	private String credential;
	private Workspace workspace;
	private String client;
	private String syncID;
	private P4Revision buildChange;
	private String charset;

	public TagAction(Run<?, ?> run) throws IOException, InterruptedException {
		super(run);
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
		task.setCredential(credential);
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

	public void setCredential(String credential) {
		this.credential = credential;
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
		P4BaseCredentials auth = ConnectionHelper.findCredential(credential);
		String p4port = auth.getP4port();
		return p4port;
	}

	public String getClient() {
		return client;
	}

	public String getSyncID() {
		return syncID;
	}

	public String getUser() {
		P4BaseCredentials auth = ConnectionHelper.findCredential(credential);
		String p4user = auth.getUsername();
		return p4user;
	}

	public String getTicket() {
		ConnectionHelper p4 = new ConnectionHelper(credential, null);
		String p4ticket = p4.getTicket();
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
	 * @throws Exception push up stack
	 */
	public Label getLabel(String tag) throws Exception {
		ClientHelper p4 = new ClientHelper(credential, null, client, charset);
		Label label = p4.getLabel(tag);
		return label;
	}

	/**
	 * Change reporting...
	 *
	 * @param run      The current build
	 * @param listener Listener for logging
	 * @return Perforce change
	 */
	public static P4Revision getLastChange(Run<?, ?> run, TaskListener listener, String client) {
		P4Revision last = null;

		List<TagAction> actions = lastActions(run);
		if (actions == null || client == null || client.isEmpty()) {
			listener.getLogger().println("No previous build found...");
			return last;
		}

		// look for action matching view
		for (TagAction action : actions) {
			if (client.equals(action.getSyncID())) {
				last = action.getBuildChange();
				listener.getLogger().println("Found last change " + last.toString() + " on syncID " + client);
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
