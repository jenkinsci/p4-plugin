package org.jenkinsci.plugins.p4.workspace;

public enum WorkspaceSpecType {

	WRITABLE("", 0), GRAPH("graph", 20171), READONLY("readonly", 20162), PARTITIONED("partitioned", 20162);

	private final String id;
	private final int minVersion;

	WorkspaceSpecType(String id, int minVersion){
		this.id = id;
		this.minVersion = minVersion;
	}

	public String getId() {
		return id;
	}

	public int getMinVersion() {
		return minVersion;
	}
}
