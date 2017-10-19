package org.jenkinsci.plugins.p4.publish;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class SubmitImpl extends Publish implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean reopen;
	private final String purge;

	public boolean isReopen() {
		return reopen;
	}

	public String getPurge() {
		return purge;
	}

	public int getPurgeValue() {
		int keep = 0;
		if (purge != null && !purge.isEmpty()) {
			try {
				keep = Integer.parseInt(purge);
			} catch (NumberFormatException e) {
				// ignore and return 0
			}
		}

		// Upper limit
		if (keep > 512) {
			return 512;
		}

		// only values 1-10,16,32,64,128,256,512
		if (keep > 10) {
			for (int i = 4; i < 10; i++) {
				int base = (int)Math.pow(2, i);
				if (base > keep) {
					return base;
				}
			}
		}
		return keep;
	}

	@DataBoundConstructor
	public SubmitImpl(String description, boolean onlyOnSuccess, boolean delete, boolean reopen, String purge) {
		super(description, onlyOnSuccess, delete);
		this.reopen = reopen;
		this.purge = purge;
	}

	@Extension
	@Symbol("submit")
	public static final class DescriptorImpl extends PublishDescriptor {

		@Override
		public String getDisplayName() {
			return "Submit change";
		}
	}
}