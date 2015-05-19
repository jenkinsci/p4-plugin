package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class AutoCleanImpl extends Populate {

	private final boolean replace;
	private final boolean delete;

	@DataBoundConstructor
	public AutoCleanImpl(boolean replace, boolean delete, boolean modtime, boolean quiet,
						 String pin) {
		super(true, false, modtime, quiet, pin); // normal sync; no -f, no -p
		this.replace = replace;
		this.delete = delete;
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
