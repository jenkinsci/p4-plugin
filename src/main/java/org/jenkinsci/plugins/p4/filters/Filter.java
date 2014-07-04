package org.jenkinsci.plugins.p4.filters;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

public abstract class Filter implements ExtensionPoint, Describable<Filter> {

	public FilterDescriptor getDescriptor() {
		return (FilterDescriptor) Jenkins.getInstance().getDescriptor(
				getClass());
	}

	public static DescriptorExtensionList<Filter, FilterDescriptor> all() {
		return Jenkins.getInstance()
				.<Filter, FilterDescriptor> getDescriptorList(Filter.class);
	}
}
