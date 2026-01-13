package org.jenkinsci.plugins.p4.populate;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;

public class SyncOnlyImpl extends Populate {

	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean revert;

	/**
	 * Sync only (optional have update)
	 *
	 * @param revert   revert before sync option
	 * @param have     populate have list
	 * @param force    force sync
	 * @param quiet    Perforce quiet option
	 * @param pin      Change or label to pin the sync
	 * @param parallel Parallel sync option
	 */
	@DataBoundConstructor
	public SyncOnlyImpl(boolean revert, boolean have, boolean force, boolean quiet, String pin, ParallelSync parallel) {
		super(have, force, quiet, pin, parallel);
		this.revert = revert;
	}

	@Deprecated
	public SyncOnlyImpl(boolean revert, boolean have, boolean quiet, String pin, ParallelSync parallel) {
		super(have, false, quiet, pin, parallel);
		this.revert = revert;
	}

	public boolean isRevert() {
		return revert;
	}

	@Extension
	@Symbol("syncOnly")
	public static final class DescriptorImpl extends PopulateDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Sync only";
		}

		@Override
		public boolean isGraphCompatible() {
			return false;
		}
	}
}