package org.jenkinsci.plugins.p4.workspace;

public enum WorkspaceSpecType {

	WRITABLE(""), GRAPH("graph"), READONLY("readonly"), PARTITIONED("partitioned");

	private final String id;

	WorkspaceSpecType(String id){
		this.id = id;
	}

	public String getId() {
		return id;
	}
}
