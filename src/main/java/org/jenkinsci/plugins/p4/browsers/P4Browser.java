package org.jenkinsci.plugins.p4.browsers;

import hudson.scm.RepositoryBrowser;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4Ref;

import java.io.Serial;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class P4Browser extends RepositoryBrowser<P4ChangeEntry> {

	@Serial
	private static final long serialVersionUID = 1L;

	private String url;

	public P4Browser(String url) {
		this.url = url;
	}

	public final String getUrl() {
		return url;
	}

	public URL getSafeUrl() {
		try {
			URL safe = normalizeToEndWithSlash(new URL(url));
			return safe;
		} catch (MalformedURLException e) {
			return null;
		}
	}

	/**
	 * Determines the link to the diff between the version.
	 *
	 * @param file   Perforce file spec
	 * @param change changelist number
	 * @return null if the browser doesn't have any URL for diff.
	 * @throws Exception push up stack
	 */
	public abstract URL getDiffLink(P4AffectedFile file, P4Ref change) throws Exception;

	/**
	 * Determines the link to a single file under Perforce. This page should
	 * display all the past revisions of this file, etc.
	 *
	 * @param file Perforce file spec
	 * @return null if the browser doesn't have any suitable URL.
	 * @throws Exception push up stack
	 */
	public abstract URL getFileLink(P4AffectedFile file) throws Exception;

	/**
	 * Determines the link for associated Perforce jobs.
	 *
	 * @param job ID
	 * @return null if the browser doesn't have any suitable URL.
	 * @throws Exception push up stack
	 */
	public abstract URL getJobLink(String job) throws Exception;

	protected int parseRevision(P4AffectedFile file) {
		String rev = file.getRevision();
		if (rev == null || !rev.contains("#") || !(rev.length() > 1)) {
			// nothing to diff
			return -1;
		}
		rev = rev.substring(1);
		return Integer.parseInt(rev);
	}
}
