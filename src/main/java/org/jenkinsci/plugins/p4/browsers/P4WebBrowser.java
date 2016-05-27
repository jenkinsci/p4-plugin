package org.jenkinsci.plugins.p4.browsers;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;

public class P4WebBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	public final URL url;

	// 'ac' stands for action and corresponds to a unique screen in P4Web
	public final String p4FileEnd = "?ac=22"; // file contents screen
	public final String p4DiffEnd = "?ac=19"; // file comparison screen
	public final String p4ChangeEnd = "?ac=10"; // change list content screen
	public final String p4JobEnd = "?ac=111"; // job content screen
	public final String p4LabelEnd = "?ac=16"; // label content screen

	@DataBoundConstructor
	public P4WebBrowser(URL url) {
		this.url = normalizeToEndWithSlash(url);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(url.toString() + changeSet.getId() + p4ChangeEnd);
	}

	public URL getLabelSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(url.toString() + changeSet.getId() + p4LabelEnd);
	}

	@Override
	public URL getDiffLink(IFileSpec file) throws Exception {
		if (file.getAction() != FileAction.EDIT
				&& file.getAction() != FileAction.INTEGRATE) {
			return null;
		}
		int r = file.getEndRevision();
		if (r <= 1) {
			return null;
		}
		return new URL(url.toString() + file.getDepotPathString() + p4DiffEnd
				+ "&rev1=" + (r - 1) + "&rev2=" + (r));
	}

	@Override
	public URL getFileLink(IFileSpec file) throws Exception {
		return new URL(url.toString() + file.getDepotPathString() + p4FileEnd);
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		return new URL(url.toString() + job + p4JobEnd);
	}

	@Extension
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
		public P4WebBrowser newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return req.bindParameters(P4WebBrowser.class, "p4web.");
		}
	}
}
