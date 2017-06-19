package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;

import java.util.List;

public class P4Head extends SCMHead {

	private final List<String> paths;
	private final boolean stream;

	P4Head(String name, List<String> paths, boolean stream) {
		super(name);
		this.paths = paths;
		this.stream = stream;
	}

	public List<String> getPaths() {
		return paths;
	}

	public boolean isStream() {
		return stream;
	}
}