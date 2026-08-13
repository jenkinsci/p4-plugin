package org.jenkinsci.plugins.p4.filters;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class FilterPerChangeImpl extends Filter implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean perChange;

	@DataBoundConstructor
	public FilterPerChangeImpl(boolean perChange) {
		this.perChange = perChange;
	}

	public boolean isPerChange() {
		return perChange;
	}

	@Extension
	@Symbol("incremental")
	public static final class DescriptorImpl extends FilterDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Polling per change";
		}
	}

	public static boolean isActive(List<Filter> filter) {
		if (filter == null) {
			return false;
		}
		for (Filter f : filter) {
			if (f instanceof FilterPerChangeImpl) {
				if (((FilterPerChangeImpl) f).isPerChange()) {
					return true;
				}
			}
		}
		return false;
	}
}
