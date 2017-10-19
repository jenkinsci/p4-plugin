package org.jenkinsci.plugins.p4.filters;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

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
	@Symbol("viewFilter")
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Exclude changes outside view mask";
		}
		
	}
}
