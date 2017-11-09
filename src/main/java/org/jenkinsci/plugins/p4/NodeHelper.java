package org.jenkinsci.plugins.p4;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import jenkins.model.Jenkins;

public class NodeHelper {


    public static final String UNKNOWN_NODE_NAME = "unknown";
    public static final String MASTER_NODE_NAME = "master";

    private NodeHelper(){}

    public static String getNodeName(Run<?,?> run) {
        String node;
        Executor executor = run.getExecutor();
        if(executor == null){
            node = UNKNOWN_NODE_NAME;
        } else if (executor.getOwner() instanceof Jenkins.MasterComputer) {
            node = MASTER_NODE_NAME;
        } else {
            node = executor.getOwner().getName() ;
        }
        return node;
    }

    public static String getNodeName(FilePath path) {
        Node node = workspaceToNode(path);
        String nodeName = nameOf(node);
        return nodeName.isEmpty() ? MASTER_NODE_NAME : nodeName;
    }

    public static String nameOf(Node node){
        return node == null ? UNKNOWN_NODE_NAME : node.getNodeName();
    }
    /**
     * Helper: find the Remote/Local Computer used for build
     *
     * @param workspace
     */
    public static Computer workspaceToComputer(FilePath workspace) {
        if (workspace != null ) {
            return workspace.toComputer();
        }
        return null;
    }

    /**
     * Helper: find the Node for slave build or return current instance.
     *
     * @param workspace
     */
    public static Node workspaceToNode(FilePath workspace) {
        Computer computer = workspaceToComputer(workspace);
        if (computer != null) {
            return computer.getNode();
        }
        return Jenkins.getInstance();
    }
}
