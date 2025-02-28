package org.jenkinsci.plugins.p4.populate;

import com.perforce.p4java.option.client.ParallelSyncOptions;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;

public class ParallelSync extends AbstractDescribableImpl<ParallelSync> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

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

	public ParallelSyncOptions getParallelOptions() {
		int threads = 0;
		int minfiles = 0;
		int minbytes = 0;

		try {
			threads = Integer.parseInt(this.threads);
			minfiles = Integer.parseInt(this.minfiles);
			minbytes = Integer.parseInt(this.minbytes);
		} catch (NumberFormatException e) {
		}

		ParallelSyncOptions opts = new ParallelSyncOptions(0, 0, minfiles, minbytes, threads, null);
		return opts;
	}

	@Extension
	@Symbol("parallel")
	public static class DescriptorImpl extends Descriptor<ParallelSync> {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce Parallel Sync";
		}
	}
}
