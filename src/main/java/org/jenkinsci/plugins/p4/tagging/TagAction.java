package org.jenkinsci.plugins.p4.tagging;

import hudson.model.Run;
import hudson.scm.AbstractScmTagAction;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.core.ILabelMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.Label.LabelMapping;

public class TagAction extends AbstractScmTagAction {

	private String tag;
	private List<String> tags = new ArrayList<String>();
	private String credential;
	private String client;
	private Object buildChange;

	public TagAction(Run<?, ?> run) {
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
		labelBuild(name, description);

		rsp.sendRedirect(".");
	}

	public void labelBuild(String name, String description) throws Exception {

		tag = name;
		ClientHelper p4 = new ClientHelper(credential, null, client);
		Label label = new Label();

		label.setDescription(description);
		label.setName(name);
		label.setRevisionSpec("@" + buildChange);

		// set label view to match workspace
		ViewMap<ILabelMapping> viewMapping = new ViewMap<ILabelMapping>();
		ClientView view = p4.getClientView();
		for (IClientViewMapping entry : view) {
			String left = entry.getLeft();
			LabelMapping lblMap = new LabelMapping();
			lblMap.setLeft(left);
			viewMapping.addEntry(lblMap);
		}
		label.setViewMapping(viewMapping);

		// save label
		if (!tags.contains(name)) {
			tags.add(name);
			getRun().save();
		}

		// update Perforce
		p4.setLabel(label);
		p4.disconnect();
	}

	public void setBuildChange(Object buildChange) {
		this.buildChange = buildChange;
	}

	public Object getBuildChange() {
		return buildChange;
	}

	public String getClient() {
		return client;
	}

	public void setCredential(String credential) {
		this.credential = credential;
	}

	public void setClient(String client) {
		this.client = client;
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
