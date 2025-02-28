package org.jenkinsci.plugins.p4.build;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import jenkins.model.Jenkins;

public class NodeHelper {

	private static final String UNKNOWN_NODE_NAME = "unknown";
	private static final String MASTER_NODE_NAME = "master";

	private NodeHelper() {
	}

	/**
	 * Helper: find the node name used for the build
	 *
	 * @param path Jenkins workspace on build node
	 * @return Node name
	 */
	public static String getNodeName(FilePath path) {
		Node node = workspaceToNode(path);
		String nodeName = nameOf(node);
		return nodeName.isEmpty() ? MASTER_NODE_NAME : nodeName;
	}

	private static String nameOf(Node node) {
		return node == null ? UNKNOWN_NODE_NAME : node.getNodeName();
	}

	/**
	 * Helper: find the Remote/Local Computer used for build
	 *
	 * @param workspace Jenkins workspace on build node
	 * @return Computer
	 */
	private static Computer workspaceToComputer(FilePath workspace) {
		if (workspace != null) {
			return workspace.toComputer();
		}
		return null;
	}

	/**
	 * Helper: find the Node for slave build or return current instance.
	 *
	 * @param workspace Jenkins workspace on build node
	 * @return Node
	 */
	public static Node workspaceToNode(FilePath workspace) {
		Computer computer = workspaceToComputer(workspace);
		if (computer != null) {
			return computer.getNode();
		}
		return Jenkins.get();
	}
}
