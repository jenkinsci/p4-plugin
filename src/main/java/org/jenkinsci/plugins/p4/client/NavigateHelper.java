package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.IDepot;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.option.server.GetDirectoriesOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.AutoCompletionCandidates;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NavigateHelper implements Closeable {

	private static Logger logger = Logger.getLogger(NavigateHelper.class.getName());

	private final int max;
	private final IOptionsServer p4;
	private final String root;

	private List<Node> nodes;

	public NavigateHelper(IOptionsServer p4) {
		this.max = 0;
		this.p4 = p4;

		String client = p4.getCurrentClient().getName();
		this.root = "//" + client + "/";
	}

	public NavigateHelper(int max) {
		this.max = max;
		p4 = ConnectionFactory.getConnection();
		root = "";
	}

	/**
	 * Matches for a partial depot path
	 *
	 * @param depotPath a Perforce Depot path e.g. //depot/pro
	 * @return matches for the depot path e.g. //depot/projA
	 */
	public AutoCompletionCandidates getCandidates(String depotPath) {
		nodes = new ArrayList<>();
		buildPaths(depotPath);
		return getCandidates();
	}

	/**
	 * Get a list of path nodes.
	 *
	 * @param localPath a relative local path e.g. "" for root or "projA/comX"
	 * @return list of nodes
	 */
	public List<Node> getNodes(String localPath) {
		nodes = new ArrayList<>();

		String path = root + localPath;
		if (!path.isEmpty() && !path.endsWith("/")) {
			path = path + "/";
		}
		buildPaths(path);

		return nodes;
	}

	private void buildPaths(String value) {
		try {
			if (!value.startsWith("//")) {
				value = "//" + value;
			}

			// remove leading '//' markers for depot matching
			String depot = value.substring(2);
			if (!depot.contains("/")) {
				if (!listDepots(depot)) {
					return;
				}
			}

			listDirs(value);
			listFiles(value);
		} catch (RequestException | AccessException e) {
			String user = p4.getUserName();
			logger.info("Removing loginCache entry for: " + user);
			ConnectionHelper.invalidateSession(user);
		} catch (P4JavaException e) {
			logger.warning(e.getMessage());
		}
	}

	/**
	 * @param value path to match
	 * @return true if value is a depot, false if partial match. e.g. false is returned
	 * for 'dep' as it is only partial match to 'depot', even thought there may be only one match.
	 * @throws P4JavaException
	 */
	private boolean listDepots(String value) throws P4JavaException {
		if (p4 != null) {
			List<IDepot> list = p4.getDepots();
			for (IDepot l : list) {
				if (l.getName().equals(value)) {
					// complete match, return early
					nodes = new ArrayList<>();
					return true;
				}
				if (l.getName().startsWith(value)) {
					nodes.add(new Node("//" + l.getName(), true));
				}
			}
		}
		return false;
	}

	private void listDirs(String value) throws P4JavaException {
		if (p4 != null && value.length() > 4) {

			List<IFileSpec> dirs = specBuilder(value);

			GetDirectoriesOptions opts = new GetDirectoriesOptions();
			List<IFileSpec> list = p4.getDirectories(dirs, opts);

			if (list == null) {
				return;
			}
			if (max > 0 && list.size() > max) {
				list = list.subList(0, max);
			}

			for (IFileSpec l : list) {
				String dir = l.getOriginalPathString();
				if (dir != null) {
					nodes.add(new Node(dir, true));
				}
			}
		}
	}

	private void listFiles(String value) throws P4JavaException {
		if (p4 != null && value.length() > 4) {

			List<IFileSpec> files = specBuilder(value);

			GetDepotFilesOptions opts = new GetDepotFilesOptions();
			if (max > 0) {
				opts.setMaxResults(max);
			}

			List<IFileSpec> list = p4.getDepotFiles(files, opts);

			for (IFileSpec l : list) {
				if (l.getOpStatus().equals(FileSpecOpStatus.VALID)) {
					nodes.add(new Node(l.getDepotPathString(), false));
				}
			}
		}
	}

	private List<IFileSpec> specBuilder(String value) {
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(value + "*");
		return files;
	}

	private AutoCompletionCandidates getCandidates() {
		AutoCompletionCandidates c = new AutoCompletionCandidates();
		for (Node node : nodes) {
			c.add(node.getDepotPath());
		}
		return c;
	}

	@Override
	public void close() throws IOException {
		try {
			p4.disconnect();
		} catch (P4JavaException e) {
			throw new IOException(e);
		}
	}

	public static final class Node {

		private String name;
		private String depotPath;
		private boolean isDir;

		private Node(String depotPath, boolean isDir) {
			this.isDir = isDir;
			this.depotPath = depotPath;
			this.name = depotPath.substring(depotPath.lastIndexOf("/") + 1);

			if (isDir && !depotPath.endsWith("/")) {
				this.depotPath = depotPath + "/";
			}
		}

		public String getName() {
			return name;
		}

		public String getDepotPath() {
			return depotPath;
		}

		public boolean isDir() {
			return isDir;
		}
	}
}
