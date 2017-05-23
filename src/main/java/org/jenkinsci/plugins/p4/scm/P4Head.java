package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;

public class P4Head extends SCMHead {

	private final String path;
	private final boolean stream;

	P4Head(String name, String path, boolean stream) {
		super(name);
		this.path = path;
		this.stream = stream;
	}

	public String getPath() {
		return path;
	}

	public boolean isStream() {
		return stream;
	}
}