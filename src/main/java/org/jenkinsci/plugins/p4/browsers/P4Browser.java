package org.jenkinsci.plugins.p4.browsers;

import com.perforce.p4java.core.file.IFileSpec;
import hudson.scm.RepositoryBrowser;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;

import java.net.URL;

public abstract class P4Browser extends RepositoryBrowser<P4ChangeEntry> {

	private static final long serialVersionUID = 1L;

	/**
	 * Determines the link to the diff between the version.
	 * 
	 * @param file  Perforce file spec
	 * @return null if the browser doesn't have any URL for diff.
	 * @throws Exception push up stack
	 */
	public abstract URL getDiffLink(IFileSpec file) throws Exception;

	/**
	 * Determines the link to a single file under Perforce. This page should
	 * display all the past revisions of this file, etc.
	 * 
	 * @param file  Perforce file spec
	 * @return null if the browser doesn't have any suitable URL.
	 * @throws Exception push up stack
	 */
	public abstract URL getFileLink(IFileSpec file) throws Exception;

	/**
	 * Determines the link for associated Perforce jobs.
	 *
	 * @param job ID
	 * @return null if the browser doesn't have any suitable URL.
	 * @throws Exception push up stack
	 */
	public abstract URL getJobLink(String job) throws Exception;

}
