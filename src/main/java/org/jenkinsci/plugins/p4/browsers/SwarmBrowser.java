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
import org.kohsuke.stapler.StaplerRequest2;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URL;

public class SwarmBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public SwarmBrowser(String url) {
		super(url);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getSafeUrl() + "change/" + changeSet.getId());
	}

	public URL getLabelSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getSafeUrl() + "label/" + changeSet.getId());
	}

	@Override
	public URL getDiffLink(P4AffectedFile file, P4Ref change) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URL getFileLink(P4AffectedFile file) throws Exception {
		int r = parseRevision(file);
		String path = file.getPath();
		path = path.replace("//", "files/");
		String rev = "?v=" + r;
		return new URL(getSafeUrl() + path + rev);
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		return new URL(getSafeUrl() + "jobs/" + job);
	}

	@Extension
	@Symbol("swarm")
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
		public SwarmBrowser newInstance(StaplerRequest2 req, JSONObject jsonObject) throws FormException {
			return (req == null) ? null : req.bindJSON(SwarmBrowser.class, jsonObject);
		}
	}
}
