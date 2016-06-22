package org.jenkinsci.plugins.p4.publish;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class SubmitImpl extends Publish implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean reopen;

	public boolean isReopen() {
		return reopen;
	}

	@DataBoundConstructor
	public SubmitImpl(String description, boolean onlyOnSuccess, boolean delete, boolean reopen) {
		super(description, onlyOnSuccess, delete);
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