package org.jenkinsci.plugins.p4.scm;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class P4Path implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String path;

	private String revision;
	private List<String> mappings = new ArrayList<>();

	public P4Path(String path) {
		this.path = path;
	}

	public void setRevision(String revision) {
		this.revision = revision;
	}

	public void setMappings(List<String> mappings) {
		this.mappings.addAll(mappings);
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

	public String getNode() {
		return path.substring(path.lastIndexOf("/") + 1);
	}

	public String getRevision() {
		if (revision != null && revision.startsWith("refs/heads/")) {
			return revision.substring("refs/heads/".length());
		}
		return revision;
	}

	public List<String> getMappings() {
		return mappings;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getPath());
		String rev = getRevision();
		rev = (rev == null) ? "latest" : rev;
		sb.append("@");
		sb.append(rev);
		return sb.toString();
	}
}
