package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.util.Map;

public abstract class Workspace implements Cloneable, ExtensionPoint, Describable<Workspace> {

	private String charset;
	private boolean pinHost;
	private String rootPath;
	private String hostname;
	private Expand expand;

	public Workspace(String charset, boolean pinHost) {
		this.charset = charset;
		this.pinHost = pinHost;
	}

	public abstract WorkspaceType getType();

	/**
	 * Returns the client workspace name as defined in the configuration. This
	 * may include ${tag} that have not been expanded.
	 *
	 * @return Client name
	 */
	public abstract String getName();

	public String getCharset() {
		return charset;
	}

	public boolean isPinHost() {
		return pinHost;
	}

	/**
	 * Setup/Create a Perforce workspace for this mode.
	 *
	 * @param connection Server connection
	 * @param user       Perforce user
	 * @return Perforce client
	 * @throws Exception push up stack
	 */
	public abstract IClient setClient(IOptionsServer connection, String user) throws Exception;

	public WorkspaceDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (WorkspaceDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<Workspace, WorkspaceDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<Workspace, WorkspaceDescriptor>getDescriptorList(Workspace.class);
		}
		return null;
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

	public void setExpand(Map<String, String> map) {
		expand = new Expand(map);
	}

	public Expand getExpand() {
		return expand;
	}

	/**
	 * Returns the fully expanded client workspace name.
	 *
	 * @return Client name
	 */
	public String getFullName() {
		// expands Workspace name if formatters are used.
		String clientName = expand.format(getName(), false);

		// replace restricted characters with "-" as per the old plugin
		clientName = clientName.replaceAll(" ", "_");
		clientName = clientName.replaceAll(",", "-");
		clientName = clientName.replaceAll("=", "-");
		clientName = clientName.replaceAll("/", "-");

		// store full name in expand options for use in view
		expand.set("P4_CLIENT", clientName);

		return clientName;
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
}
