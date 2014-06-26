package org.jenkinsci.plugins.p4_client.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class SyncOnlyImpl extends Populate {

	/**
	 * Sync only (optional have update)
	 * 
	 * @param have
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean have, String pin) {
		super(have, false, pin);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Sync only";
		}
	}
}
