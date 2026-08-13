package org.jenkinsci.plugins.p4.publish;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.Serial;
import java.io.Serializable;

public abstract class Publish implements ExtensionPoint, Describable<Publish>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String description;
	private final boolean onlyOnSuccess;
	private final boolean delete;
	private final boolean modtime;

	private String expandedDesc;
	private String paths;

	public String getDescription() {
		return description;
	}

	public boolean isOnlyOnSuccess() {
		return onlyOnSuccess;
	}

	public boolean isDelete() {
		return delete;
	}

	public boolean isModtime() {
		return modtime;
	}

	public Publish(String description, boolean onlyOnSuccess, boolean delete, boolean modtime) {
		this.description = description;
		this.onlyOnSuccess = onlyOnSuccess;
		this.delete = delete;
		this.modtime = modtime;
	}

	public PublishDescriptor getDescriptor() {
		Jenkins j = Jenkins.get();
		return (PublishDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<Publish, PublishDescriptor> all() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorList(Publish.class);
	}

	public String getExpandedDesc() {
		return expandedDesc == null ? description : expandedDesc;
	}

	public void setExpandedDesc(String expandedDesc) {
		this.expandedDesc = expandedDesc;
	}

	protected void setPaths(String paths) {
		this.paths = paths;
	}

	public String getPaths() {
		return paths;
	}
}
