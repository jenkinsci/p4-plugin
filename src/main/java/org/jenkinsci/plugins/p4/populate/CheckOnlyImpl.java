package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class CheckOnlyImpl extends Populate {

	/**
	 * No sync, check change only
	 * 
	 * @param pin
	 */
	@DataBoundConstructor
	public CheckOnlyImpl(boolean have, boolean force, boolean modtime, boolean quiet,
			String pin) {
		super(false, false, false, quiet, null);
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
			return "Preview check Only";
		}
	}

}
