package org.jenkinsci.plugins.p4.filters;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class FilterLatestWithPinImpl extends Filter implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean latestWithPin;

	@DataBoundConstructor
	public FilterLatestWithPinImpl(boolean latestWithPin) {
		this.latestWithPin = latestWithPin;
	}

	public boolean isLatestWithPin() {
		return latestWithPin;
	}

	@Extension
	@Symbol("latestWithPin")
	public static final class DescriptorImpl extends FilterDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Polling latest change with pin";
		}
	}

	public static boolean isActive(List<Filter> filter) {
		if (filter == null) {
			return false;
		}
		for (Filter f : filter) {
			if (f instanceof FilterLatestWithPinImpl) {
				if (((FilterLatestWithPinImpl) f).isLatestWithPin()) {
					return true;
				}
			}
		}
		return false;
	}
}
