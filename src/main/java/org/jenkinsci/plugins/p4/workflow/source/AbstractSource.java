package org.jenkinsci.plugins.p4.workflow.source;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serializable;

public abstract class AbstractSource implements ExtensionPoint, Describable<AbstractSource>, Serializable {
	private static final long serialVersionUID = 1L;

	public abstract Workspace getWorkspace(String charset, String format);

	public P4SyncDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (P4SyncDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<AbstractSource, P4SyncDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<AbstractSource, P4SyncDescriptor>getDescriptorList(AbstractSource.class);
		}
		return null;
	}

	public static String getClientView(String src, String dest) {
		return ViewMapHelper.getClientView(src, dest);
	}
}
