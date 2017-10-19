package org.jenkinsci.plugins.p4.filters;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class FilterPerChangeImpl extends Filter implements Serializable {

	private static final long serialVersionUID = 1L;

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
	@Symbol("incremental")
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Polling per Change";
		}
	}
}
