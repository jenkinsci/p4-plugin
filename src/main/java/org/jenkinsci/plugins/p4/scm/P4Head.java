package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;

public class P4Head extends SCMHead {

	private final P4Path paths;

	P4Head(String name, P4Path paths) {
		super(name);
		this.paths = paths;
	}

	public P4Path getPath() {
		return paths;
	}
}