package org.jenkinsci.plugins.p4.filters;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.NavigateHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

public class FilterPathImpl extends Filter implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String path;

	@DataBoundConstructor
	public FilterPathImpl(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

	@Extension
	@Symbol("pathFilter")
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Exclude changes from Depot path";
		}

		public AutoCompletionCandidates doAutoCompletePath(@QueryParameter String value) {
			NavigateHelper nav = new NavigateHelper(10);
			return nav.getCandidates(value);
		}
	}
}
