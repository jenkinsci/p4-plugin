package org.jenkinsci.plugins.p4.filters;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class FilterPerChangeImpl extends Filter {
	
	private final boolean perChange;
	private int nextChange;
	
	@DataBoundConstructor
	public FilterPerChangeImpl(boolean perChange) {
		this.perChange = perChange;
	}

	public boolean isPerChange() {
		return perChange;
	}

	public int getNextChange() {
		return nextChange;
	}

	public void setNextChange(int nextChange) {
		this.nextChange = nextChange;
	}

	@Extension
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Polling per Change";
		}
	}
}
