package org.jenkinsci.plugins.p4.browsers;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;

public class FishEyeBrowser extends P4Browser {

	private static final long serialVersionUID = 1L;

	/**
	 * The URL of the FishEye repository, e.g.
	 * <tt>http://deadlock.netbeans.org/fisheye/browse/netbeans/</tt>
	 */
	public final URL url;

	/**
	 * This is the root 'module' of the FishEye repository. It is a path that is
	 * trimmed from the beginning of depot paths for files.
	 */
	public final String rootModule;

	@DataBoundConstructor
	public FishEyeBrowser(URL url, String rootModule) {
		this.url = normalizeToEndWithSlash(url);
		this.rootModule = trimHeadSlash(trimHeadSlash(rootModule));
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(url, "../../changelog/" + getProjectName() + "/?cs="
				+ changeSet.getId());
	}

	@Override
	public URL getDiffLink(IFileSpec file) throws Exception {
		if (file.getAction() != FileAction.EDIT
				&& file.getAction() != FileAction.INTEGRATE) {
			return null;
		}
		int change = file.getChangelistId();
		if (change <= 1) {
			return null;
		}
		return new URL(url, getRelativeFilename(file)
				+ new QueryBuilder(url.getQuery()).add("r1=").add(
						"r2=" + change));
	}

	@Override
	public URL getFileLink(IFileSpec file) throws Exception {
		return new URL(url, getRelativeFilename(file)
				+ new QueryBuilder(url.getQuery()));
	}

	@Override
	public URL getJobLink(String job) throws Exception {
		// Not implemented for FishEye
		return null;
	}

	private String getRelativeFilename(IFileSpec file) {
		String path = trimHeadSlash(trimHeadSlash(file.getDepotPathString()));
		if (path.startsWith(getRootModule())) {
			path = path.substring(getRootModule().length());
		}
		return trimHeadSlash(path);
	}

	/**
	 * Pick up "FOOBAR" from "http://site/browse/FOOBAR/"
	 */
	private String getProjectName() {
		String p = url.getPath();
		if (p.endsWith("/"))
			p = p.substring(0, p.length() - 1);

		int idx = p.lastIndexOf('/');
		return p.substring(idx + 1);
	}

	private String getRootModule() {
		if (rootModule == null)
			return "";
		return rootModule;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

		private static final Pattern URL_PATTERN = Pattern
				.compile(".+/browse/[^/]+/");

		@Override
		public String getDisplayName() {
			return "FishEye browser";
		}

		public FormValidation doCheck(@QueryParameter final String value)
				throws IOException, ServletException {

			String url = Util.fixEmpty(value);
			if (url == null) {
				return FormValidation.ok();
			}
			if (!url.endsWith("/")) {
				url += '/';
			}
			if (!URL_PATTERN.matcher(url).matches()) {
				return FormValidation
						.errorWithMarkup("The URL should end like <tt>.../browse/foobar/</tt>");
			}
			return FormValidation.ok();
		}

		@Override
		public P4WebBrowser newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return req.bindParameters(P4WebBrowser.class, "fisheye.");
		}
	}
}
