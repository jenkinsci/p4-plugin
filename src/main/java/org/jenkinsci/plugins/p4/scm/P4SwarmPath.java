package org.jenkinsci.plugins.p4.scm;

import java.util.List;

public class P4SwarmPath extends P4Path {

	private final List<String> mappings;

	public P4SwarmPath(String path, List<String> mappings) {
		super(path);

		this.mappings = mappings;
	}

	public P4SwarmPath(String path, List<String> mappings, String revision) {
		super(path, revision);

		this.mappings = mappings;
	}

	public List<String> getMappings() {
		return mappings;
	}
}
