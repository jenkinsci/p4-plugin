package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class ForceCleanImpl extends Populate {

	/**
	 * Force sync of workspace (optional have update)
	 *
	 * @param have
	 */
	@DataBoundConstructor
	public ForceCleanImpl(boolean have, boolean modtime, String label) {
		super(have, true, modtime, label);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Forced clean and sync";
		}
	}
}
