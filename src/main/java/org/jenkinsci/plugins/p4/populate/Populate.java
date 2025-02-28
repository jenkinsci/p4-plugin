package org.jenkinsci.plugins.p4.populate;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.Serial;
import java.io.Serializable;

public abstract class Populate implements ExtensionPoint, Describable<Populate>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean have; // ! sync '-p'
	private final boolean force; // sync '-f'
	private final boolean modtime;
	private final boolean quiet; // task '-q'
	private final String pin;
	private final ParallelSync parallel;

	public Populate(boolean have, boolean force, boolean modtime, boolean quiet, String pin, ParallelSync parallel) {
		this.have = have;
		this.force = force;
		this.modtime = modtime;
		this.pin = pin;
		this.quiet = quiet;
		this.parallel = parallel;
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

	public boolean isQuiet() {
		return quiet;
	}

	public String getPin() {
		return pin;
	}

	public ParallelSync getParallel() {
		return parallel;
	}

	public PopulateDescriptor getDescriptor() {
		Jenkins j = Jenkins.get();
		return (PopulateDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<Populate, PopulateDescriptor> all() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorList(Populate.class);
	}
}
