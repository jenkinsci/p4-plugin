package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;

public class P4Head extends SCMHead {

	private final P4Path path;

	public P4Head(String name, P4Path path) {
		super(name);
		this.path = path;
	}

	public P4Path getPath() {
		return path;
	}

	@Override
	public String toString() {
		return "P4Head: " + getName() + " (" + path + ")";
	}
}