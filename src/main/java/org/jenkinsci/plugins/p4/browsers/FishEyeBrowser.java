package org.jenkinsci.plugins.p4.browsers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.io.Serial;
import java.net.URL;
import java.util.regex.Pattern;

public class FishEyeBrowser extends P4Browser {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * The URL of the FishEye repository, e.g.
	 * <tt>http://deadlock.netbeans.org/fisheye/browse/netbeans/</tt>
	 */

	/**
	 * This is the root 'module' of the FishEye repository. It is a path that is
	 * trimmed from the beginning of depot paths for files.
	 */
	private final String rootModule;

	public String getRootModule() {
		if (rootModule == null)
			return "";
		return rootModule;
	}

	@DataBoundConstructor
	public FishEyeBrowser(String url, String rootModule) {
		super(url);
		this.rootModule = trimHeadSlash(trimHeadSlash(rootModule));
	}

	@Override
	public URL getChangeSetLink(P4ChangeEntry changeSet) throws IOException {
		return new URL(getSafeUrl(), "../../changelog/" + getProjectName() + "/?cs="
				+ changeSet.getId());
	}

	@Override
	public URL getDiffLink(P4AffectedFile file, P4Ref change) throws Exception {
		if (!file.getAction().equalsIgnoreCase("edit")) {
			return null;
		}
		if (change == null) {
			return null;
		}
		return new URL(getSafeUrl(), getRelativeFilename(file)
				+ new QueryBuilder(getSafeUrl().getQuery()).add("r1=").add(
				"r2=" + change));
	}

	@Override
	public URL getFileLink(P4AffectedFile file) throws Exception {
		return new URL(getSafeUrl(), getRelativeFilename(file)
				+ new QueryBuilder(getSafeUrl().getQuery()));
	}

	@Override
	public URL getJobLink(String job) {
		// Not implemented for FishEye
		return null;
	}

	private String getRelativeFilename(P4AffectedFile file) {
		String path = trimHeadSlash(trimHeadSlash(file.getPath()));
		if (path.startsWith(getRootModule())) {
			path = path.substring(getRootModule().length());
		}
		return trimHeadSlash(path);
	}

	/**
	 * Pick up "FOOBAR" from "http://site/browse/FOOBAR/"
	 */
	private String getProjectName() {
		String p = getSafeUrl().getPath();
		if (p.endsWith("/"))
			p = p.substring(0, p.length() - 1);

		int idx = p.lastIndexOf('/');
		return p.substring(idx + 1);
	}

	@Extension
	@Symbol("fishEye")
	public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

		private static final Pattern URL_PATTERN = Pattern
				.compile(".+/browse/[^/]+/");

		@NonNull
		@Override
		public String getDisplayName() {
			return "FishEye browser";
		}

		public FormValidation doCheck(@QueryParameter final String value) {

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
		public FishEyeBrowser newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) {
			return (req == null) ? null : req.bindJSON(FishEyeBrowser.class, jsonObject);
		}
	}
}
