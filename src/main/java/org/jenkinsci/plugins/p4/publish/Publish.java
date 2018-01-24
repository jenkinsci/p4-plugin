package org.jenkinsci.plugins.p4.publish;

import java.io.Serializable;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

public abstract class Publish implements ExtensionPoint, Describable<Publish>, Serializable {

	private static final long serialVersionUID = 1L;

	private final String description;
	private final boolean onlyOnSuccess;
	private final boolean delete;

	private String expandedDesc;

	public String getDescription() {
		return description;
	}

	public boolean isOnlyOnSuccess() {
		return onlyOnSuccess;
	}

	public boolean isDelete() {
		return delete;
	}

	public Publish(String description, boolean onlyOnSuccess, boolean delete) {
		this.description = description;
		this.onlyOnSuccess = onlyOnSuccess;
		this.delete = delete;
	}

	public PublishDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (PublishDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<Publish, PublishDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<Publish, PublishDescriptor> getDescriptorList(Publish.class);
		}
		return null;
	}

	public String getExpandedDesc() {
		return expandedDesc == null ? description : expandedDesc;
	}

	public void setExpandedDesc(String expandedDesc) {
		this.expandedDesc = expandedDesc;
	}
}
