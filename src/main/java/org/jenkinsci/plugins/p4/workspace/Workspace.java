package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.review.ReviewProp;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class Workspace implements Cloneable, ExtensionPoint,
		Describable<Workspace> {

	private static Logger logger = Logger.getLogger(Workspace.class.getName());

	private String charset;
	private boolean pinHost;
	private String rootPath;
	private String hostname;
	private Map<String, String> formatTags;

	public Workspace(String charset, boolean pinHost) {
		this.charset = charset;
		this.pinHost = pinHost;
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

	public boolean isPinHost() {
		return pinHost;
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
		return (WorkspaceDescriptor) Jenkins.getInstance().getDescriptor(
				getClass());
	}

	public static DescriptorExtensionList<Workspace, WorkspaceDescriptor> all() {
		return Jenkins.getInstance()
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

	public String expand(String format, boolean wildcard) {
		if (formatTags != null) {
			for (String tag : formatTags.keySet()) {
				String value = formatTags.get(tag);
				if (value != null) {
					format = format.replace("${" + tag + "}", value);
				}
			}
		}

		// cleanup undefined tags
		if (wildcard) {
			format = format.replaceAll("\\$\\{.+?\\}", "*");
		}
		format = format.replace("${", "");
		format = format.replace("}", "");
		return format;
	}

	public void set(String tag, String value) {
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
			String value = map.get(key);
			if (ReviewProp.isProp(key)) {
				// Known Perforce Review property; prefix with namespace
				key = ReviewProp.NAMESPACE + key;
			}
			set(key, value);
		}
	}

	/**
	 * Returns the fully expanded client workspace name.
	 * 
	 * @return
	 */
	public String getFullName() {
		// expands Workspace name if formatters are used.
		String clientName = expand(getName(), false);

		// replace restricted characters with "-" as per the old plugin
		clientName = clientName.replaceAll(" ", "_");
		clientName = clientName.replaceAll(",", "-");
		clientName = clientName.replaceAll("=", "-");
		clientName = clientName.replaceAll("/", "-");
		return clientName;
	}

	public void clear() {
		formatTags = new HashMap<String, String>();
		Jenkins jenkins = Jenkins.getInstance();

		for (NodeProperty<?> node : jenkins.getGlobalNodeProperties()) {
			if (node instanceof EnvironmentVariablesNodeProperty) {
				EnvironmentVariablesNodeProperty env = (EnvironmentVariablesNodeProperty) node;
				formatTags.putAll((env).getEnvVars());
			}
		}
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
}
