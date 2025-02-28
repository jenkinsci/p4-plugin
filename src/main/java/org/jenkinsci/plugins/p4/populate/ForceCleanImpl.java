package org.jenkinsci.plugins.p4.populate;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;

public class ForceCleanImpl extends Populate {

	@Serial
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

		@NonNull
		@Override
		public String getDisplayName() {
			return "Forced clean and sync";
		}

		@Override
		public boolean isGraphCompatible() {
			return false;
		}
	}
}
