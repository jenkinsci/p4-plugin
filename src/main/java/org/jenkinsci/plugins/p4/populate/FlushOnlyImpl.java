package org.jenkinsci.plugins.p4.populate;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class FlushOnlyImpl extends Populate {

	private static final long serialVersionUID = 1L;

	/**
	 * No sync, check change only
	 *
	 * @param have    populate have list
	 * @param force   force sync
	 * @param modtime use MODTIME for reconcile
	 * @param quiet   Perforce quiet option
	 * @param pin     Change or label to pin the sync
	 */
	@DataBoundConstructor
	public FlushOnlyImpl(boolean have, boolean force, boolean modtime, boolean quiet,
	                     String pin) {
		super(true, false, false, quiet, null, null); 
	}

	@Extension
	public static final class DescriptorImpl extends PopulateDescriptor {

		@Override
		public String getDisplayName() {
                    return "Flush workspace";
		}
	}
}
