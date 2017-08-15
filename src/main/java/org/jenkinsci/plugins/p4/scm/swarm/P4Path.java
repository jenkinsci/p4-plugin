package org.jenkinsci.plugins.p4.scm.swarm;

import java.io.File;

public class P4Path {

	private final String path;
	private final String revision;

	public P4Path(String path, String revision) {
		this.path = path;
		this.revision = revision;
	}

	public P4Path(String path) {
		this.path = path;
		this.revision = null;
	}

	public String getPathBuilder(String file) {
		String filePath = path + File.separator + file;
		if (getRevision() == null) {
			return filePath;
		} else {
			return filePath + "@" + getRevision();
		}
	}

	public String getName() {
		String p = getPath();
		if (p.startsWith("//")) {
			p = p.substring("//".length());
			p = p.replaceAll("/", ".");
		}

		String r = getRevision();
		if (r != null && r.startsWith("refs/pull/")) {
			r = r.substring("refs/pull/".length());
		}
		r = r.replaceAll("/", ".");

		return p + "." + r;
	}

	public String getPath() {
		return path;
	}

	public String getRevision() {
		if (revision != null && revision.startsWith("refs/heads/")) {
			return revision.substring("refs/heads/".length());
		}
		return revision;
	}
}
