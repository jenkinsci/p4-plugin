package org.jenkinsci.plugins.p4.workspace;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;

public abstract class Workspace implements ExtensionPoint,
		Describable<Workspace> {

	private static Logger logger = Logger.getLogger(Workspace.class.getName());

	private String charset;
	private String rootPath;
	private String hostname;
	private Map<String, String> formatTags;

	public Workspace(String charset) {
		this.charset = charset;
	}

	public abstract WorkspaceType getType();

	/**
	 * Returns the client workspace name as defined in the configuration. This
	 * may include ${tag} that have not been expanded.
	 * 
	 * @return
	 */
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

	public String expand(String format) {
		if (formatTags != null) {
			for (String tag : formatTags.keySet()) {
				String value = formatTags.get(tag);
				if (value != null) {
					format = format.replace("${" + tag + "}", value);
				}
			}
		}

		// cleanup undefined tags
		format = format.replace("${", "");
		format = format.replace("}", "");
		return format;
	}

	public void set(String tag, String value) {
		if (formatTags == null) {
			formatTags = new HashMap<String, String>();
			Jenkins jenkins = Jenkins.getInstance();

			for (NodeProperty<?> node : jenkins.getGlobalNodeProperties()) {
				if (node instanceof EnvironmentVariablesNodeProperty) {
					EnvironmentVariablesNodeProperty env = (EnvironmentVariablesNodeProperty) node;
					formatTags.putAll((env).getEnvVars());
				}
			}
		}
		formatTags.put(tag, value);
	}
	
	public String get(String tag) {
		if (formatTags == null) {
			return null;
		}
		return formatTags.get(tag);
	}

	public void load(Map<String, String> map) {
		for (String key : map.keySet()) {
			set(key, map.get(key));
		}
	}

	/**
	 * Returns the fully expanded client workspace name.
	 * 
	 * @return
	 */
	public String getFullName() {
		// expands Workspace name if formatters are used.
		String clientName = expand(getName());
		clientName = clientName.replaceAll(" ", "_");
		return clientName;
	}
}
