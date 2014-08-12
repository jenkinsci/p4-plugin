package org.jenkinsci.plugins.p4.credentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TrustImpl extends AbstractDescribableImpl<TrustImpl> implements
		Serializable {

	private static final long serialVersionUID = 1L;
	
	@NonNull
	private final String trust;

	@DataBoundConstructor
	public TrustImpl(@CheckForNull String trust) {
		this.trust = trust;
	}

	@NonNull
	public String getTrust() {
		return trust;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TrustImpl> {
		@Override
		public String getDisplayName() {
			return "SSL Trust";
		}
	}
}
