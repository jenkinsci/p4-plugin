package org.jenkinsci.plugins.p4.populate;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class ParallelSync extends AbstractDescribableImpl<ParallelSync> {

	private final boolean enable;
	private final String path;
	private final String threads;
	private final String minfiles;
	private final String minbytes;

	@DataBoundConstructor
	public ParallelSync(boolean enable, String path, String threads, String minfiles, String minbytes) {
		this.enable = enable;
		this.path = path;
		this.threads = threads;
		this.minfiles = minfiles;
		this.minbytes = minbytes;
	}

	public boolean isEnable() {
		return enable;
	}

	public String getPath() {
		return path;
	}

	public String getThreads() {
		return threads;
	}

	public String getMinfiles() {
		return minfiles;
	}

	public String getMinbytes() {
		return minbytes;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<ParallelSync> {

		@Override
		public String getDisplayName() {
			return "Perforce Parallel Sync";
		}
	}

}
