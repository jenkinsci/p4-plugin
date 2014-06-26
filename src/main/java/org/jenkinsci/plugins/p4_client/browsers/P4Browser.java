package org.jenkinsci.plugins.p4_client.browsers;

import hudson.scm.RepositoryBrowser;

import java.net.URL;

import org.jenkinsci.plugins.p4_client.changes.P4ChangeEntry;

import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.IFileSpec;

public abstract class P4Browser extends RepositoryBrowser<P4ChangeEntry> {

	private static final long serialVersionUID = 1L;

	/**
	 * Determines the link to the diff between the version. in the
	 * {@link PerforceChangeLogEntry.Change.File} to its previous version.
	 * 
	 * @return null if the browser doesn't have any URL for diff.
	 */
	public abstract URL getDiffLink(IFileSpec file) throws Exception;

	/**
	 * Determines the link to a single file under Perforce. This page should
	 * display all the past revisions of this file, etc.
	 * 
	 * @return null if the browser doesn't have any suitable URL.
	 */
	public abstract URL getFileLink(IFileSpec file) throws Exception;

	public abstract URL getJobLink(IJob job) throws Exception;

}
