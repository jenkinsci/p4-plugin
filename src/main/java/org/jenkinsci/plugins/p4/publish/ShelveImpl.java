package org.jenkinsci.plugins.p4.publish;

import hudson.Extension;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class ShelveImpl extends Publish implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean revert;

	public boolean isRevert() {
		return revert;
	}

	@DataBoundConstructor
	public ShelveImpl(String description, boolean revert) {
		super(description);
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
