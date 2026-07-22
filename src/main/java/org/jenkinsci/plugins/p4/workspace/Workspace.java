package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public abstract class Workspace implements Cloneable, ExtensionPoint, Describable<Workspace>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String charset;
	private boolean pinHost;
	private String rootPath;
	private String hostname;
	private Expand expand;
	private String syncID;
	private boolean cleanup;

	public Workspace(String charset, boolean pinHost, boolean cleanup) {
		this.charset = charset;
		this.pinHost = pinHost;
		this.cleanup = cleanup;
	}

	public abstract WorkspaceType getType();

	/**
	 * Returns the client workspace name as defined in the configuration. This
	 * may include ${tag} that have not been expanded.
	 *
	 * @return Client name
	 */
	public abstract String getName();

	public abstract void setName(String name);

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
		Jenkins j = Jenkins.get();
		return (WorkspaceDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<Workspace, WorkspaceDescriptor> all() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorList(Workspace.class);
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
		// provide an empty map if Environment is not set.
		if (expand == null) {
			setExpand(new HashMap<>());
		}
		return expand;
	}

	public void addEnv(String tag, String value) {
		expand.set(tag, value);
	}

	/**
	 * Returns the fully expanded client workspace name.
	 *
	 * @return Client name
	 */
	public String getFullName() {
		if (expand == null) {
			return getName();
		}

		// expands Workspace name if formatters are used.
		String clientName = expand.format(getName(), false);

		// replace restricted characters with "-" as per the old plugin
		clientName = expand.clean(clientName);

		// store full name in expand options for use in view
		expand.set("P4_CLIENT", clientName);

		return clientName;
	}

	public String getSyncID() {
		String id = null;

		if (expand == null) {
			return id;
		}

		// if syncID provide expand or use client name.
		if (syncID != null && !syncID.isEmpty()) {
			id = expand.formatID(syncID);
		} else {
			id = expand.formatID(getName());
		}

		// replace restricted characters with "-" as per the old plugin
		id = expand.clean(id);

		// remove .cloneNN during concurrent builds
		id = id.replaceAll(".clone\\d+$", "");

		return id;
	}

	@DataBoundSetter
	public void setSyncID(String syncID) {
		this.syncID = syncID;
	}

	public boolean isCleanup() {
		return cleanup;
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public Workspace deepClone() throws AbortException {
		try {
			return (Workspace) this.clone();
		} catch (CloneNotSupportedException e) {
			throw new AbortException(e.getMessage());
		}
	}
}
