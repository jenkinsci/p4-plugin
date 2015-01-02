package org.jenkinsci.plugins.p4.publish;

import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;

public class SubmitImpl extends Publish {

	private final boolean reopen;

	public boolean isReopen() {
		return reopen;
	}

	@DataBoundConstructor
	public SubmitImpl(String description, boolean reopen) {
		super(description);
		this.reopen = reopen;
	}

	@Extension
	public static final class DescriptorImpl extends PublishDescriptor {

		@Override
		public String getDisplayName() {
			return "Submit change";
		}
	}
}