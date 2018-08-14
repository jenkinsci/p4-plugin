package org.jenkinsci.plugins.p4.filters;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.Serializable;

public abstract class Filter implements ExtensionPoint, Describable<Filter>, Serializable {

	private static final long serialVersionUID = 1L;

	public FilterDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		return (FilterDescriptor) j.getDescriptor(getClass());
	}

	public static DescriptorExtensionList<Filter, FilterDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		return j.<Filter, FilterDescriptor> getDescriptorList(Filter.class);
	}
}
