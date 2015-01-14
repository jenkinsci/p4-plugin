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

	private String expandedDesc;

	public String getDescription() {
		return description;
	}

	public Publish(String description) {
		this.description = description;
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
