package org.jenkinsci.plugins.p4.client;

import java.util.List;

public class ViewMapHelper {

	private static final String QUOTE = "\"";
	private static final String MAP_SEP = " ";
	private static final String MAP_DELIM = "\n";
	private static final String PATH_DELIM = "/";
	private static final String EXCLUDE = "-";
	private static final String INCLUDE = "+";

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

	public static String getClientView(String depotView, String client) {
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
		StringBuffer view = processLines(lines, client);

		return view.toString();
	}

	public static String getClientView(List<String> views, String client) {
		// exit early if no views
		if (views == null || views.isEmpty()) {
			return null;
		}

		// exit early if no client is defined
		if (client == null || client.isEmpty()) {
			return null;
		}

		StringBuffer view = processLines(views.toArray(new String[0]), client);

		return view.toString();
	}

	private static StringBuffer processLines(String[] lines, String client) {

		StringBuffer view = new StringBuffer();

		boolean multi = lines.length > 1;

		for (int c = 0; c < lines.length; c++) {
			// detect space characters for later
			boolean spaces = lines[c].contains(" ");
			boolean exclude = lines[c].startsWith(EXCLUDE);
			boolean include = lines[c].startsWith(INCLUDE);

			// remove leading "//", "+//" or "-//" from path
			String remove = "//";
			remove = (exclude) ? EXCLUDE + "//" : remove;
			remove = (include) ? INCLUDE + "//" : remove;
			String path = lines[c].substring(remove.length());

			// split path on "/" depot deliminator
			String[] parts = path.split(PATH_DELIM);

			// process depot and client mappings
			StringBuffer lhs = processLHS(parts);
			StringBuffer rhs = processRHS(client, parts, multi);

			// Add Exclude/Include mappings
			if(exclude) {
				lhs.insert(0, EXCLUDE);
			}
			if(include) {
				lhs.insert(0, INCLUDE);
			}

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

		return view;
	}

	private static StringBuffer processLHS(String[] parts) {
		StringBuffer lhs = new StringBuffer("//");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				lhs.append(PATH_DELIM);
			}
			lhs.append(parts[i]);
		}
		return lhs;
	}

	private static StringBuffer processRHS(String client, String[] parts, boolean multi) {
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
