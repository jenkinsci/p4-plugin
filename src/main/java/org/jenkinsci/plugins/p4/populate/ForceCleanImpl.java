package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class ForceCleanImpl extends Populate {

	private static final long serialVersionUID = 1L;

	/**
	 * Force sync of workspace (optional have update)
	 *
	 * @param have     populate have list
	 * @param quiet    Perforce quiet option
	 * @param pin      Change or label to pin the sync
	 * @param parallel Parallel sync options
	 */
	@DataBoundConstructor
	public ForceCleanImpl(boolean have, boolean quiet, String pin, ParallelSync parallel) {
		super(have, true, false, quiet, pin, parallel);
	}

	@Extension
	@Symbol("forceClean")
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Forced clean and sync";
		}
	}
}
