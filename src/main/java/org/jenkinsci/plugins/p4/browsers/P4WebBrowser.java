package org.jenkinsci.plugins.p4.browsers;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;

public class P4WebBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	// 'ac' stands for action and corresponds to a unique screen in P4Web
	public final static String p4FileEnd = "?ac=22"; // file contents screen
	public final static String p4DiffEnd = "?ac=19"; // file comparison screen
	public final static String p4ChangeEnd = "?ac=10"; // change list content screen
	public final static String p4JobEnd = "?ac=111"; // job content screen
	public final static String p4LabelEnd = "?ac=16"; // label content screen

	@DataBoundConstructor
	public P4WebBrowser(String url) {
		super(url);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getSafeUrl().toString() + changeSet.getId() + p4ChangeEnd);
	}

	public URL getLabelSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getSafeUrl().toString() + changeSet.getId() + p4LabelEnd);
	}

	@Override
	public URL getDiffLink(P4AffectedFile file, P4Ref change) throws Exception {
		if (!file.getAction().equalsIgnoreCase("edit")) {
			return null;
		}

		int rev = parseRevision(file);
		if(rev <= 1) {
			// nothing to diff
			return null;
		}

		return new URL(getSafeUrl().toString() + file.getPath() + p4DiffEnd
				+ "&rev1=" + (rev - 1) + "&rev2=" + (rev));
	}

	@Override
	public URL getFileLink(P4AffectedFile file) throws Exception {
		return new URL(getSafeUrl().toString() + file.getPath() + p4FileEnd);
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		return new URL(getSafeUrl().toString() + job + p4JobEnd);
	}

	@Extension
	@Symbol("p4Web")
	public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

		@Override
		public String getDisplayName() {
			return "P4Web browser";
		}

		public FormValidation doCheck(@QueryParameter final String value)
				throws IOException, ServletException {

			String url = Util.fixEmpty(value);
			if (url == null) {
				return FormValidation.ok();
			}
			if (!url.startsWith("http://") && !url.startsWith("https://")) {
				return FormValidation
						.errorWithMarkup("The URL should contain <tt>http://</tt> or <tt>https://</tt>");
			}
			return FormValidation.ok();
		}

		@Override
		public P4WebBrowser newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
			return (req == null) ? null : req.bindJSON(P4WebBrowser.class, jsonObject);
		}
	}
}
