package org.jenkinsci.plugins.p4.filters;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.Serial;
import java.io.Serializable;

public abstract class Filter implements ExtensionPoint, Describable<Filter>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public FilterDescriptor getDescriptor() {
		Jenkins j = Jenkins.get();
		return (FilterDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<Filter, FilterDescriptor> all() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorList(Filter.class);
	}
}
