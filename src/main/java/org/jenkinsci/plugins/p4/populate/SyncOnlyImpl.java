package org.jenkinsci.plugins.p4.populate;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class SyncOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;

	private final boolean revert;

	/**
	 * Sync only (optional have update)
	 *
	 * @param revert   revert before sync option
	 * @param have     populate have list
	 * @param modtime  use MODTIME for reconcile
	 * @param quiet    Perforce quiet option
	 * @param pin      Change or label to pin the sync
	 * @param parallel Parallel sync option
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean revert, boolean have, boolean modtime, boolean quiet, String pin, ParallelSync parallel) {
		super(have, false, modtime, quiet, pin, parallel);
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
