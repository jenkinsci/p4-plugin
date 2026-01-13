package org.jenkinsci.plugins.p4.filters;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class FilterLatestChangeImpl extends Filter implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean latestChange;

	@DataBoundConstructor
	public FilterLatestChangeImpl(boolean latestChange) {
		this.latestChange = latestChange;
	}

	public boolean isLatestChange() {
		return latestChange;
	}

	@Extension
	@Symbol("latest")
	public static final class DescriptorImpl extends FilterDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Polling latest change";
		}
	}

	public static boolean isActive(List<Filter> filter) {
		if (filter == null) {
			return false;
		}
		for (Filter f : filter) {
			if (f instanceof FilterLatestChangeImpl) {
				if (((FilterLatestChangeImpl) f).isLatestChange()) {
					return true;
				}
			}
		}
		return false;
	}
}
