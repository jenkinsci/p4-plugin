package org.jenkinsci.plugins.p4.filters;

import hudson.model.Descriptor;

public abstract class FilterDescriptor extends Descriptor<Filter> {

	public FilterDescriptor(Class<? extends Filter> clazz) {
		super(clazz);
	}

	protected FilterDescriptor() {
	}
}
