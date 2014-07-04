package org.jenkinsci.plugins.p4.populate;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;

import java.io.Serializable;

import jenkins.model.Jenkins;

public abstract class Populate implements ExtensionPoint,
		Describable<Populate>, Serializable {

	private static final long serialVersionUID = 1L;
	
	private final boolean have; // ! sync '-p'
	private final boolean force; // sync '-f'
	private final String pin;

	public Populate(boolean have, boolean force, String pin) {
		this.have = have;
		this.force = force;
		this.pin = pin;
	}

	public boolean isHave() {
		return have;
	}

	public boolean isForce() {
		return force;
	}

	public String getPin() {
		return pin;
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
