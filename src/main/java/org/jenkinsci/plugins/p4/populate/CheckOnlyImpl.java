package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CheckOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;

	/**
	 * No sync, check change only. (p4 sync -n ...)
	 *
	 * @param quiet   Perforce quiet option
	 * @param pin     Change or label to pin the sync
	 */
	@DataBoundConstructor
	public CheckOnlyImpl(boolean quiet, String pin) {
		super(false, false, false, quiet, pin, null);
	}

	@Extension
	@Symbol("previewOnly")
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Preview check Only";
		}
	}
}
