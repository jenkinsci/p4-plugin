package org.jenkinsci.plugins.p4.populate;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.Serializable;

public abstract class Populate implements ExtensionPoint,
		Describable<Populate>, Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean have; // ! sync '-p'
	private final boolean force; // sync '-f'
	private final boolean modtime;
	private final String label;

	public Populate(boolean have, boolean force, boolean modtime, String label) {
		this.have = have;
		this.force = force;
		this.modtime = modtime;
		this.label = label;
	}

	public boolean isHave() {
		return have;
	}

	public boolean isForce() {
		return force;
	}

	public boolean isModtime() {
		return modtime;
	}

	public String getLabel() {
		return label;
	}

	public PopulateDescriptor getDescriptor() {
		return (PopulateDescriptor) Jenkins.getInstance().getDescriptor(
				getClass());
	}

	public static DescriptorExtensionList<Populate, PopulateDescriptor> all() {
		return Jenkins.getInstance()
				.<Populate, PopulateDescriptor> getDescriptorList(
						Populate.class);
	}
}
