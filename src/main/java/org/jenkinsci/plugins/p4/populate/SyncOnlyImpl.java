package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class SyncOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Sync only (optional have update)
	 * 
	 * @param have
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean have, boolean modtime, boolean quiet, String pin) {
		super(have, false, modtime, quiet, pin);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Sync only";
		}
	}
}
