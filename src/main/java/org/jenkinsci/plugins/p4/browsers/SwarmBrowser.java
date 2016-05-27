package org.jenkinsci.plugins.p4.browsers;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.perforce.p4java.core.file.IFileSpec;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

public class SwarmBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	public final URL url;

	@DataBoundConstructor
	public SwarmBrowser(URL url) {
		this.url = normalizeToEndWithSlash(url);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(url.toString() + "change/" + changeSet.getId());
	}

	public URL getLabelSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(url.toString() + "label/" + changeSet.getId());
	}

	@Override
	public URL getDiffLink(IFileSpec file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URL getFileLink(IFileSpec file) throws Exception {
		int r = file.getEndRevision();
		String path = file.getDepotPathString();
		path = path.replace("//", "files/");
		String rev = "?v=" + r;
		return new URL(url.toString() + path + rev);
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		return new URL(url.toString() + "jobs/" + job);
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

		@Override
		public String getDisplayName() {
			return "Swarm browser";
		}

		public FormValidation doCheck(@QueryParameter final String value) throws IOException, ServletException {

			String url = Util.fixEmpty(value);
			if (url == null) {
				return FormValidation.ok();
			}
			if (!url.startsWith("http://") && !url.startsWith("https://")) {
				return FormValidation.errorWithMarkup("The URL should contain <tt>http://</tt> or <tt>https://</tt>");
			}
			return FormValidation.ok();
		}

		@Override
		public SwarmBrowser newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return req.bindParameters(SwarmBrowser.class, "swarm.");
		}
	}
}
