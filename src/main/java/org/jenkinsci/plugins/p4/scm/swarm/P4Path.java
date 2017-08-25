package org.jenkinsci.plugins.p4.scm.swarm;

import java.io.Serializable;

public class P4Path implements Serializable {

	private static final long serialVersionUID = 1L;

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
		String filePath = path + "/" + file;
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
		if (r != null) {
			if(r.startsWith("refs/pull/")) {
				r = r.substring("refs/pull/".length());
			}
			r = r.replaceAll("/", ".");
		}

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
