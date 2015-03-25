package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class SyncOnlyImpl extends Populate {

	/**
	 * Sync only (optional have update)
	 *
	 * @param have
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean have, boolean modtime, String label) {
		super(have, false, modtime, label);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Sync only";
		}
	}
}
