package org.jenkinsci.plugins.p4.publish;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class ShelveImpl extends Publish implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean revert;

	public boolean isRevert() {
		return revert;
	}

	@DataBoundConstructor
	public ShelveImpl(String description, boolean onlyOnSuccess, boolean delete, boolean modtime, boolean revert) {
		super(description, onlyOnSuccess, delete, modtime);
		this.revert = revert;
	}

	@Extension
	@Symbol("shelve")
	public static final class DescriptorImpl extends PublishDescriptor {

		@Override
		public String getDisplayName() {
			return "Shelve change";
		}
	}
}
