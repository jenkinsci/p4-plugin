package org.jenkinsci.plugins.p4.publish;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;

import java.io.Serializable;

import jenkins.model.Jenkins;

public abstract class Publish implements ExtensionPoint, Describable<Publish>,
		Serializable {

	private static final long serialVersionUID = 1L;

	private final String description;
	private final boolean onlyOnSuccess;

	private String expandedDesc;

	public String getDescription() {
		return description;
	}

	public boolean isOnlyOnSuccess() {
		return onlyOnSuccess;
	}

	public Publish(String description, boolean onlyOnSuccess) {
		this.description = description;
		this.onlyOnSuccess = onlyOnSuccess;
	}

	public PublishDescriptor getDescriptor() {
		return (PublishDescriptor) Jenkins.getInstance().getDescriptor(
				getClass());
	}

	public static DescriptorExtensionList<Publish, PublishDescriptor> all() {
		return Jenkins.getInstance()
				.<Publish, PublishDescriptor> getDescriptorList(Publish.class);
	}

	public String getExpandedDesc() {
		return expandedDesc;
	}

	public void setExpandedDesc(String expandedDesc) {
		this.expandedDesc = expandedDesc;
	}
}
