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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;

public class OpenGrokBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	/**
	 * The URL of the OpenGrok server, e.g.
	 * <tt>http://opengrok.libreoffice.org/</tt>
	 */

	/**
	 * The Perforce depot path for the 'project', e.g.
	 * <tt>//depot/core/main</tt>
	 */
	private final String depotPath;

	public String getDepotPath() {
		return depotPath;
	}

	/**
	 * The name of the 'project' in OpenGrok, e.g. <tt>core</tt>
	 */
	private final String projectName;

	public String getProjectName() {
		return projectName;
	}

	@DataBoundConstructor
	public OpenGrokBrowser(String url, String depotPath, String projectName) {
		super(url);
		this.depotPath = depotPath;
		this.projectName = projectName;
	}

	@Override
	public URL getDiffLink(P4AffectedFile file, String change) throws Exception {
		String path = getRelativeFilename(file);

		int rev = parseRevision(file);
		if(rev <= 1) {
			return null;
		}

		String r1 = "r1=" + URLEncoder.encode(path + "#" + (rev - 1), "UTF-8");
		String r2 = "r2=" + URLEncoder.encode(path + "#" + rev, "UTF-8");

		return new URL(getSafeUrl(), "source/diff/" + projectName + "/build.properties?"
				+ r2 + "&" + r1 + getRelativeFilename(file));
	}

	@Override
	public URL getFileLink(P4AffectedFile file) throws Exception {
		return new URL(getSafeUrl(), "source/xref/" + projectName + "/"
				+ getRelativeFilename(file));
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	private String getRelativeFilename(P4AffectedFile file) {
		String path = file.getPath();
		if (path.startsWith(depotPath)) {
			path = path.substring(depotPath.length());
		}
		return trimHeadSlash(path);
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Extension
	@Symbol("openGrok")
	public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

		@Override
		public String getDisplayName() {
			return "OpenGrok";
		}

		public FormValidation doCheck(@QueryParameter final String value) {
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
		public OpenGrokBrowser newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
			return (req == null) ? null : req.bindJSON(OpenGrokBrowser.class, jsonObject);
		}
	}
}
