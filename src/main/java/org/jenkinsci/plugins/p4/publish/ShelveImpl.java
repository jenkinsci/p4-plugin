package org.jenkinsci.plugins.p4.publish;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class ShelveImpl extends Publish implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean revert;

	public boolean isRevert() {
		return revert;
	}

	@DataBoundConstructor
	public ShelveImpl(String description, boolean onlyOnSuccess, boolean delete, boolean revert) {
		super(description, onlyOnSuccess, delete);
		this.revert = revert;
	}

	@Extension
	public static final class DescriptorImpl extends PublishDescriptor {

		@Override
		public String getDisplayName() {
			return "Shelve change";
		}
	}
}
