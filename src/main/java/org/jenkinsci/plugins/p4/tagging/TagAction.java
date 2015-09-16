package org.jenkinsci.plugins.p4.tagging;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.scm.AbstractScmTagAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

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

import com.perforce.p4java.impl.generic.core.Label;

public class TagAction extends AbstractScmTagAction {

	private String tag;
	private List<String> tags = new ArrayList<String>();

	private String credential;
	private Workspace workspace;
	private String client;
	private P4Revision buildChange;

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

	public void doSubmit(StaplerRequest req, StaplerResponse rsp)
			throws Exception, ServletException {

		getACL().checkPermission(PerforceScm.TAG);

		String description = req.getParameter("desc");
		String name = req.getParameter("name");
		labelBuild(null, name, description, null);

		rsp.sendRedirect(".");
	}

	public void labelBuild(TaskListener listener, String name,
			String description, final FilePath nodeWorkspace) throws Exception {
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

		// Invoke the Label Task
		buildWorkspace.act(task);

		// save label
		if (!tags.contains(name)) {
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
	}

	public String getPort() {
		P4BaseCredentials auth = ConnectionHelper.findCredential(credential);
		String p4port = auth.getP4port();
		return p4port;
	}

	public String getClient() {
		return client;
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
	 * @param tag
	 * @return
	 * @throws Exception
	 */
	public Label getLabel(String tag) throws Exception {
		ClientHelper p4 = new ClientHelper(credential, null, client);
		Label label = p4.getLabel(tag);
		return label;
	}
}
