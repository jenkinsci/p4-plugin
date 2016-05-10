package org.jenkinsci.plugins.p4.populate;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class AutoCleanImpl extends Populate {

	private static final long serialVersionUID = 1L;

	private final boolean replace;
	private final boolean delete;

	@DataBoundConstructor
	public AutoCleanImpl(boolean replace, boolean delete, boolean modtime, boolean quiet, String pin,
			ParallelSync parallel) {
		// normal sync; no -f, no -p
		super(true, false, modtime, quiet, pin, parallel);
		this.replace = replace;
		this.delete = delete;
	}

	// Default for test cases
	public AutoCleanImpl() {
		super(true, true, false, false, null, null);
		this.replace = false;
		this.delete = false;
	}

	public boolean isReplace() {
		return replace;
	}

	public boolean isDelete() {
		return delete;
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Auto cleanup and sync";
		}
	}
}
