package org.jenkinsci.plugins.p4.filters;

import hudson.Extension;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class FilterViewMaskImpl extends Filter implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String viewMask;

	@DataBoundConstructor
	public FilterViewMaskImpl(String viewMask) {
		this.viewMask = viewMask;
	}

	public String getViewMask() {
		return viewMask;
	}

	@Extension
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Exclude changes outside view mask";
		}
		
	}
}
