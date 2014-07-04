package org.jenkinsci.plugins.p4.workspace;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;

public abstract class Workspace implements ExtensionPoint,
		Describable<Workspace> {

	private String charset;
	private String rootPath;
	private String hostname;
	private Map<String, String> formatTags;

	public Workspace(String charset) {
		this.charset = charset;
	}

	public abstract WorkspaceType getType();

	public abstract String getName();

	public String getCharset() {
		return charset;
	}

	/**
	 * Setup/Create a Perforce workspace for this mode.
	 * 
	 * @param connection
	 * @param user
	 * @return Perforce client
	 * @throws Exception
	 */
	public abstract IClient setClient(IOptionsServer connection, String user)
			throws Exception;

	public WorkspaceDescriptor getDescriptor() {
		return (WorkspaceDescriptor) Hudson.getInstance().getDescriptor(
				getClass());
	}

	public static DescriptorExtensionList<Workspace, WorkspaceDescriptor> all() {
		return Hudson.getInstance()
				.<Workspace, WorkspaceDescriptor> getDescriptorList(
						Workspace.class);
	}

	public String getRootPath() {
		return rootPath;
	}

	public void setHostName(String hostname) {
		this.hostname = hostname;
	}

	public String getHostName() {
		return hostname;
	}

	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}

	public Set<String> getTags() {
		return formatTags.keySet();
	}

	public String getFormattedName(String format) {
		String name = format;
		for (String tag : formatTags.keySet()) {
			String value = formatTags.get(tag);
			if (value != null) {
				name = name.replace("${" + tag + "}", value);
			}
		}
		return name;
	}

	public void set(String tag, String value) {
		if (formatTags == null)
			formatTags = new HashMap<String, String>();
		formatTags.put(tag, value);
	}
}
