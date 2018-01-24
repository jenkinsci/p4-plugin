package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class FlushOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;

	/**
	 * Populate the have list, but no files. (p4 sync -k ...)
	 *
	 * @param quiet   Perforce quiet option
	 * @param pin     Change or label to pin the sync
	 */
	@DataBoundConstructor
	public FlushOnlyImpl(boolean quiet, String pin) {
		super(true, false, false, quiet, pin, null);
	}

	@Extension
	@Symbol("flushOnly")
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Flush workspace";
		}

		@Override
		public boolean isGraphCompatible() {
			return false;
		}
	}
}
