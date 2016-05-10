package org.jenkinsci.plugins.p4.populate;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class SyncOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;

	private final boolean revert;

	/**
	 * Sync only (optional have update)
	 * 
	 * @param have
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean revert, boolean have, boolean modtime, boolean quiet, String pin) {
		super(have, false, modtime, quiet, pin, null);
		this.revert = revert;
	}

	public boolean isRevert() {
		return revert;
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Sync only";
		}
	}
}
