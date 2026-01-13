package org.jenkinsci.plugins.p4.credentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;

public class TrustImpl extends AbstractDescribableImpl<TrustImpl> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NonNull private final String trust;

	@DataBoundConstructor
	public TrustImpl(@NonNull String trust) {
		this.trust = trust;
	}

	@NonNull
	public String getTrust() {
		return trust;
	}

	@Extension
	@Symbol("trust")
	public static class DescriptorImpl extends Descriptor<TrustImpl> {
		@NonNull
		@Override
		public String getDisplayName() {
			return "SSL Trust";
		}
	}
}
