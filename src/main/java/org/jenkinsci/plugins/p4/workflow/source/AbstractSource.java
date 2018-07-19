package org.jenkinsci.plugins.p4.workflow.source;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serializable;

public abstract class AbstractSource implements ExtensionPoint, Describable<AbstractSource>, Serializable {
	private static final long serialVersionUID = 1L;

	public abstract Workspace getWorkspace(String charset, String format);

	public P4SyncDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (P4SyncDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<AbstractSource, P4SyncDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<AbstractSource, P4SyncDescriptor>getDescriptorList(AbstractSource.class);
		}
		return null;
	}

	public static String getClientView(String src, String dest) {
		return new ClientViewMappingGenerator().generateClientView(src, dest);
	}
}

/**
 * Class used to generate client view mapping given a depot syntax and client syntax
 */
class ClientViewMappingGenerator {

	private static final String ELLIPSIS = "...";
	private static final String STAR = "*";
	private static final String QUOTE = "\"";
	private static final String MAP_SEP = " ";
	private static final String MAP_DELIM = "\n";
	private static final String PATH_DELIM = "/";

	/**
	 * Generates client view mapping string.
	 * e.g  //depot/src/...  //ws/src/...
	 * <p>
	 * This method handles the case where multiple items are specified in dpotSyntax delimitted
	 * with a new line character \n, in addition to standard specification.
	 * e.g //depot/src/...\n//depot/tgt/...
	 * <p>
	 * If multiple items are speicified in depot syntax, each client syntax will be appended with
	 * the part of the depot syntax that follows //, to avoid ambiguity.
	 * <p>
	 * For instance //depot/src/...\n//depot/tgt/... with a client syntax of jenkins-job will generate the following
	 * view,
	 * <p>
	 * //depot/src/...  //jenkins-job/depot/src/...
	 * //depot/tgt/...  //jenkins-job/depot/tgt/...
	 *
	 * @param depotView The left hand side of the client view
	 * @param client    The client workspace name
	 * @return Client view mapping
	 */

	public String generateClientView(String depotView, String client) {

		StringBuffer view = new StringBuffer();

		// exit early if no depotPath
		if (depotView == null || depotView.isEmpty()) {
			return null;
		}

		// exit early if no client is defined
		if (client == null || client.isEmpty()) {
			return null;
		}

		// Split on new line and trim any following white space
		String[] lines = depotView.split("\n\\s*");
		boolean multi = lines.length > 1;

		for (int c = 0; c < lines.length; c++) {
			// detect space characters for later
			boolean spaces = lines[c].contains(" ");

			// remove leading "//" from path
			String path = lines[c].substring("//".length());

			// split path on "/" depot deliminator
			String[] parts = path.split(PATH_DELIM);

			// process depot and client mappings
			StringBuffer lhs = processLHS(parts);
			StringBuffer rhs = processRHS(client, parts, multi);

			// Wrap with quotes if spaces are used in the path
			if (spaces) {
				lhs.insert(0, QUOTE);
				lhs.append(QUOTE);
				rhs.insert(0, QUOTE);
				rhs.append(QUOTE);
			}

			// Build view
			if (c > 0) {
				view.append(MAP_DELIM);
			}
			view.append(lhs);
			view.append(MAP_SEP);
			view.append(rhs);
		}
		return view.toString();
	}

	private StringBuffer processLHS(String[] parts) {
		StringBuffer lhs = new StringBuffer("//");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				lhs.append(PATH_DELIM);
			}
			lhs.append(parts[i]);
		}
		return lhs;
	}

	private StringBuffer processRHS(String client, String[] parts, boolean multi) {
		StringBuffer rhs = new StringBuffer("//");
		rhs.append(client);
		rhs.append("/");

		// multi lines may require full depot path e.g.
		//      //depot/proj/src/... //client/depot/proj/src/...
		if (multi) {
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					rhs.append(PATH_DELIM);
				}
				rhs.append(parts[i]);
			}
		}
		// single lines can map directly to client root e.g.
		//      //depot/proj/src/... //client/...
		else {
			rhs.append(parts[parts.length - 1]);
		}
		return rhs;
	}
}