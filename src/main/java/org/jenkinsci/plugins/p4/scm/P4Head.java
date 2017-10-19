package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;

import java.util.List;

public class P4Head extends SCMHead {

	private final List<P4Path> paths;

	P4Head(String name, List<P4Path> paths) {
		super(name);
		this.paths = paths;
	}

	public List<P4Path> getPaths() {
		return paths;
	}
}