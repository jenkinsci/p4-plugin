package org.jenkinsci.plugins.p4.populate;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class ForceCleanImpl extends Populate {

	private static final long serialVersionUID = 1L;

	/**
	 * Force sync of workspace (optional have update)
	 * 
	 * @param have
	 */
	@DataBoundConstructor
	public ForceCleanImpl(boolean have, boolean modtime, boolean quiet, String pin, ParallelSync parallel) {
		super(have, true, modtime, quiet, pin, parallel);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Forced clean and sync";
		}
	}
}
