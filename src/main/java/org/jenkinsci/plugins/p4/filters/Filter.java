package org.jenkinsci.plugins.p4.filters;

import java.io.Serializable;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

public abstract class Filter implements ExtensionPoint, Describable<Filter>, Serializable {

	private static final long serialVersionUID = 1L;

	public FilterDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (FilterDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<Filter, FilterDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<Filter, FilterDescriptor> getDescriptorList(Filter.class);
		}
		return null;
	}
}
