package org.jenkinsci.plugins.p4.workflow.source;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serial;
import java.io.Serializable;

public abstract class AbstractSource implements ExtensionPoint, Describable<AbstractSource>, Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	public abstract Workspace getWorkspace(String charset, String format);

	public P4SyncDescriptor getDescriptor() {
		Jenkins j = Jenkins.get();
		return (P4SyncDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<AbstractSource, P4SyncDescriptor> all() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorList(AbstractSource.class);
	}

	public static String getClientView(String src, String dest) {
		return ViewMapHelper.getClientView(src, dest, false);
	}
}
