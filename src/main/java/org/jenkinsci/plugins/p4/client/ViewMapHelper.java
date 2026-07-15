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
	 * @param overlay   Overlay '+' mappings
	 * @return Client view mapping
	 */

	public static String getClientView(String depotView, String client, boolean overlay) {
		// exit early if no depotPath
		if (depotView == null || depotView.isEmpty()) {
			return null;
		}

		// exit early if no client is defined
		if (client == null || client.isEmpty()) {
			return null;
		}

		// Split on new line and trim any following white space
		String[] lines = depotView.trim().split("\n\\s*");
		boolean multi = lines.length > 1;
		StringBuffer view = processLines(lines, client, multi, overlay);

		return view.toString();
	}

	public static String getClientView(List<String> views, String client, boolean external, boolean overlay) {
		// exit early if no views
		if (views == null || views.isEmpty()) {
			return null;
		}

		// exit early if no client is defined
		if (client == null || client.isEmpty()) {
			return null;
		}

		StringBuffer view = processLines(views.toArray(new String[0]), client, external, overlay);

		return view.toString();
	}

	public static String getScriptView(String base, String scriptPath, String client) {
		// exit early if no client is defined
		if (client == null || client.isEmpty()) {
			return null;
		}

		StringBuffer lhs = new StringBuffer();
		lhs.append(base);
		lhs.append(PATH_DELIM);
		lhs.append(scriptPath);

		StringBuffer rhs = new StringBuffer("//");
		rhs.append(client);
		rhs.append(PATH_DELIM);
		rhs.append(scriptPath);

		// Wrap with quotes if spaces are used in the path
		if (base.contains(" ") || scriptPath.contains(" ")) {
			lhs = wrapSpaces(lhs);
			rhs = wrapSpaces(rhs);
		}

		StringBuilder view = new StringBuilder();
		view.append(lhs);
		view.append(MAP_SEP);
		view.append(rhs);

		return view.toString();
	}

	public static String[] splitDepotPath(String path) {
		boolean exclude = path.startsWith(EXCLUDE);
		boolean include = path.startsWith(INCLUDE);

		// remove leading "//", "+//" or "-//" from path
		String remove = "//";
		remove = (exclude) ? EXCLUDE + "//" : remove;
		remove = (include) ? INCLUDE + "//" : remove;
		path = path.substring(remove.length());

		// split path on "/" depot deliminator
		String[] parts = path.split(PATH_DELIM);
		return parts;
	}

	private static StringBuffer processLines(String[] lines, String client, boolean external, boolean overlay) {

		StringBuffer view = new StringBuffer();

		for (int c = 0; c < lines.length; c++) {
			// detect space characters for later
			boolean spaces = lines[c].contains(" ");
			boolean exclude = lines[c].startsWith(EXCLUDE);
			boolean include = lines[c].startsWith(INCLUDE);

			// split path on "/" depot deliminator
			String[] parts = splitDepotPath(lines[c]);

			// process depot and client mappings
			include |= !external && overlay;
			StringBuffer lhs = processLHS(parts);
			StringBuffer rhs = processRHS(client, parts, external);

			// Add Exclude/Include mappings
			if(exclude) {
				lhs.insert(0, EXCLUDE);
			} else if(include) {
				lhs.insert(0, INCLUDE);
			}

			// Wrap with quotes if spaces are used in the path
			if (spaces) {
				lhs = wrapSpaces(lhs);
				rhs = wrapSpaces(rhs);
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

	private static StringBuffer wrapSpaces(StringBuffer sb) {
		sb.insert(0, QUOTE);
		sb.append(QUOTE);
		return sb;
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

	private static StringBuffer processRHS(String client, String[] parts, boolean external) {
		StringBuffer rhs = new StringBuffer("//");
		rhs.append(client);
		rhs.append("/");

		// External mapping lines require full depot path e.g.
		//      //depot/proj/src/... //client/depot/proj/src/...
		if (external) {
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					rhs.append(PATH_DELIM);
				}
				rhs.append(parts[i]);
			}
		}
		// local mapping lines can map directly to client root e.g.
		//      //depot/proj/src/... //client/...
		else {
			rhs.append(parts[parts.length - 1]);
		}
		return rhs;
	}
}
