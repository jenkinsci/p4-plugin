package org.jenkinsci.plugins.p4.filters;

import java.io.Serializable;
import java.util.List;

import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class FilterPollMasterImpl extends Filter implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean master;
	private P4Revision lastChange;

	@DataBoundConstructor
	public FilterPollMasterImpl(boolean master) {
		this.master = master;
	}

	public boolean isMaster() {
		return master;
	}

	public P4Revision getLastChange() {
		return lastChange;
	}

	public void setLastChange(P4Revision last) {
		this.lastChange = last;
	}

	public static boolean isMasterPolling(List<Filter> filter) {
		FilterPollMasterImpl f = findSelf(filter);
		if (f != null) {
			if (f.isMaster()) {
				return true;
			}
		}
		return false;
	}

	public static FilterPollMasterImpl findSelf(List<Filter> filter) {
		if (filter != null) {
			for (Filter f : filter) {
				if (f instanceof FilterPollMasterImpl) {
					return (FilterPollMasterImpl) f;
				}
			}
		}
		return null;
	}

	@Extension
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Poll on Master using Last Build";
		}
	}
}
