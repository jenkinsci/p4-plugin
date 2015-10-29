package org.jenkinsci.plugins.p4.browsers;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;

import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.IFileSpec;

public class OpenGrokBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	/**
	 * The URL of the OpenGrok server, e.g.
	 * <tt>http://opengrok.libreoffice.org/</tt>
	 */
	public final URL url;

	/**
	 * The Perforce depot path for the 'project', e.g.
	 * <tt>//depot/core/main</tt>
	 */
	public final String depotPath;

	/**
	 * The name of the 'project' in OpenGrok, e.g. <tt>core</tt>
	 */
	public final String projectName;

	@DataBoundConstructor
	public OpenGrokBrowser(URL url, String depotPath, String projectName) {
		this.url = normalizeToEndWithSlash(url);
		this.depotPath = depotPath;
		this.projectName = projectName;
	}

	@Override
	public URL getDiffLink(IFileSpec file) throws Exception {
		if (file.getEndRevision() <= 1) {
			// nothing to diff
			return null;
		}

		String path = getRelativeFilename(file);
		int rev2 = file.getEndRevision();
		int rev1 = file.getEndRevision() - 1;

		String r1 = "r1=" + URLEncoder.encode(path + "#" + rev1, "UTF-8");
		String r2 = "r2=" + URLEncoder.encode(path + "#" + rev2, "UTF-8");

		return new URL(url, "source/diff/" + projectName + "/build.properties?"
				+ r2 + "&" + r1 + getRelativeFilename(file));
	}

	@Override
	public URL getFileLink(IFileSpec file) throws Exception {
		return new URL(url, "source/xref/" + projectName + "/"
				+ getRelativeFilename(file));
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	private String getRelativeFilename(IFileSpec file) {
		String path = file.getDepotPathString();
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
	}
}
