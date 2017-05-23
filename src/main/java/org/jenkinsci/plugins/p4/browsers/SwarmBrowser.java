package org.jenkinsci.plugins.p4.browsers;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class SwarmBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public SwarmBrowser(String url) throws MalformedURLException {
		super(url);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getUrl() + "change/" + changeSet.getId());
	}

	public URL getLabelSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getUrl() + "label/" + changeSet.getId());
	}

	@Override
	public URL getDiffLink(P4AffectedFile file, String change) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URL getFileLink(P4AffectedFile file) throws Exception {
		String r = file.getRevision();
		String path = file.getPath();
		path = path.replace("//", "files/");
		String rev = "?v=" + r;
		return new URL(getUrl() + path + rev);
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		return new URL(getUrl() + "jobs/" + job);
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
			SwarmBrowser browser = null;
			if (req != null) {
				browser = req.bindParameters(SwarmBrowser.class, "swarm.");
			}
			return browser;
		}
	}
}
