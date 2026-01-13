package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.admin.IProperty;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IDepot;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.ILabel;
import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.core.IUser;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.graph.ICommit;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.option.server.DeleteClientOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.option.server.GetDirectoriesOptions;
import com.perforce.p4java.option.server.GetFixesOptions;
import com.perforce.p4java.option.server.GetPropertyOptions;
import com.perforce.p4java.option.server.GetStreamsOptions;
import com.perforce.p4java.option.server.GraphCommitLogOptions;
import com.perforce.p4java.option.server.ReposOptions;
import com.perforce.p4java.server.CmdSpec;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.changes.P4LabelRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHelper extends SessionHelper implements AutoCloseable {

	private static Logger logger = Logger.getLogger(ConnectionHelper.class.getName());


	@Deprecated
	public ConnectionHelper(String credentialID, TaskListener listener) throws IOException {
		super(credentialID, listener);
	}

	public ConnectionHelper(ItemGroup context, String credentialID, TaskListener listener) throws IOException {
		this(findCredential(credentialID, context), listener);
	}

	public ConnectionHelper(Item job, String credentialID, TaskListener listener) throws IOException {
		this(findCredential(credentialID, job), listener);
	}

	public ConnectionHelper(Run run, String credentialID, TaskListener listener) throws IOException {
		this(findCredential(credentialID, run), listener);
	}

	public ConnectionHelper(P4BaseCredentials credential, TaskListener listener) throws IOException {
		super(credential, listener);
	}

	public ConnectionHelper(P4BaseCredentials credential) throws IOException {
		super(credential, new LogTaskListener(logger, Level.INFO));
	}


	/**
	 * Gets a list of Dirs given a path (multi-branch?)
	 *
	 * @param paths list of paths to look for dirs (only takes * as wildcard)
	 * @return list of dirs or empty list
	 * @throws Exception push up stack
	 */
	public List<IFileSpec> getDirs(List<String> paths) throws Exception {
		paths = cleanDirPaths(paths);

		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(paths);
		GetDirectoriesOptions opts = new GetDirectoriesOptions();

		List<IFileSpec> dirs = getConnection().getDirectories(spec, opts);
		if (getValidate().check(dirs, "")) {
			return dirs;
		}
		return new ArrayList<>();
	}

	private List<String> cleanDirPaths(List<String> paths) throws Exception {
		if (paths.contains("//...")) {
			return getDepotsForDirs();
		}

		ListIterator<String> list = paths.listIterator();
		while (list.hasNext()) {
			String i = list.next();
			if (i.endsWith("/...")) {
				i = i.substring(0, i.length() - "/...".length());
			}
			if (!i.endsWith("/*")) {
				list.set(i + "/*");
			}
		}
		return paths;
	}

	private List<String> getDepotsForDirs() throws Exception {
		List<String> paths = new ArrayList<>();
		List<IDepot> depots = getConnection().getDepots();
		for (IDepot depot : depots) {
			String name = depot.getName();
			paths.add("//" + name + "/*");
		}
		return paths;
	}

	/**
	 * Gets a list of Dirs given a path (multi-stream?)
	 *
	 * @param paths list of path to look for streams (takes ... or * as wildcard)
	 * @return list of streams or empty list
	 * @throws Exception push up stack
	 */
	public List<IStreamSummary> getStreams(List<String> paths) throws Exception {
		GetStreamsOptions opts = new GetStreamsOptions();
		List<IStreamSummary> streams = getConnection().getStreams(paths, opts);
		return streams;
	}

	public IChangelistSummary getChangeSummary(long id) throws P4JavaException {
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList("@" + id);
		GetChangelistsOptions cngOpts = new GetChangelistsOptions();
		cngOpts.setLongDesc(true);
		cngOpts.setMaxMostRecent(1);
		List<IChangelistSummary> summary = getConnection().getChangelists(spec, cngOpts);
		if (summary.isEmpty()) {
			return null;
		}
		return summary.get(0);
	}

	public List<IFix> getJobs(int id) throws P4JavaException {
		GetFixesOptions opts = new GetFixesOptions();
		opts.setChangelistId(id);
		List<IFix> fixes = getConnection().getFixes(null, opts);
		return fixes;
	}

	/**
	 * Test if given name is a counter
	 *
	 * @param name Couner name
	 * @return true if counter
	 * @throws Exception push up stack
	 */
	public boolean isCounter(String name) throws Exception {
		if (name.equals("now")) {
			return false;
		}
		// JENKINS-70219 - numeric counters are illegal in p4d
		if (name.matches("(\\d*)")) {
			return false;
		}
		try {
			CounterOptions opts = new CounterOptions();
			String counter = getConnection().getCounter(name, opts);
			return (!"0".equals(counter));
		} catch (RequestException e) {
			return false;
		}
	}

	/**
	 * Get Perforce Counter
	 *
	 * @param id Counter name
	 * @return Perforce Counter
	 * @throws Exception push up stack
	 */
	public String getCounter(String id) throws Exception {
		CounterOptions opts = new CounterOptions();
		String counter = getConnection().getCounter(id, opts);
		return counter;
	}

	/**
	 * Test if given name is a label
	 *
	 * @param name Label name
	 * @return true if label.
	 * @throws Exception push up stack
	 */
	public boolean isLabel(String name) throws Exception {
		if (name.equals("now")) {
			return false;
		}
		try {
			ILabel label = getConnection().getLabel(name);
			return (label != null);
		} catch (RequestException e) {
			return false;
		}
	}

	/**
	 * Look for a label and return a change or static label.
	 *
	 * @param name label
	 * @return change reference or null if no label found.
	 */
	public String labelToChange(String name) {
		try {
			Label label = getLabel(name);
			String spec = label.getRevisionSpec();
			if (spec != null && !spec.isEmpty()) {
				if (spec.startsWith("@")) {
					spec = spec.substring(1);
				}
				return spec;
			} else {
				// a label, but no RevisionSpec
				return name;
			}
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Look for a counter and return a change or reference.
	 *
	 * @param name counter
	 * @return change reference or null if no counter found.
	 */
	public String counterToChange(String name) {
		try {
			String counter = getCounter(name);
			if (!"0".equals(counter)) {
				try {
					// if a change number, add change...
					int change = Integer.parseInt(counter);
					return String.valueOf(change);
				} catch (NumberFormatException n) {
					// no change number in counter
				}
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	/**
	 * Test if given name is a client
	 *
	 * @param name Client name
	 * @return true if client exists.
	 * @throws Exception push up stack
	 */
	public boolean isClient(String name) throws Exception {
		try {
			if (name == null) {
				return false;
			}
			IClient client = getConnection().getClient(name);
			return (client != null);
		} catch (RequestException e) {
			logger.info("getClient exception " + e.getMessage());
			return false;
		}
	}

	/**
	 * Delete a client workspace
	 *
	 * @param name Client name
	 * @throws Exception push up stack
	 */
	public void deleteClient(String name) throws Exception {
		DeleteClientOptions opts = new DeleteClientOptions();
		getConnection().deleteClient(name, opts);
	}

	public String getEmail(String userName) throws Exception {
		IUser user = getConnection().getUser(userName);
		if (user != null) {
			String email = user.getEmail();
			return email;
		}
		return "";
	}

	/**
	 * Get Perforce Label
	 *
	 * @param id Label name
	 * @return Perforce Label
	 * @throws Exception push up stack
	 */
	public Label getLabel(String id) throws Exception {
		return (Label) getConnection().getLabel(id);
	}

	/**
	 * Create/Update a Perforce Label
	 *
	 * @param label Label name
	 * @throws Exception push up stack
	 */
	public void setLabel(Label label) throws Exception {
		// getConnection().createLabel(label);
		String user = getConnection().getUserName();
		label.setOwnerName(user);
		getConnection().updateLabel(label);
	}

	/**
	 * Find all files within a label or change. (Max results limited by limit)
	 *
	 * @param id    Label name or change number (as string)
	 * @param limit Max results (-m value)
	 * @return List of file specs
	 * @throws Exception push up stack
	 */
	public List<IFileSpec> getLabelFiles(String id, int limit) throws Exception {
		String path = "//...@" + id;
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(path);
		GetDepotFilesOptions opts = new GetDepotFilesOptions();
		opts.setMaxResults(limit);

		List<IFileSpec> tagged = getConnection().getDepotFiles(spec, opts);
		return tagged;
	}

	// Use a describe for files to avoid MAXSCANROW limits.
	// (backed-out part of change 16390)
	public List<IFileSpec> getChangeFiles(long id, int limit) throws Exception {
		List<IFileSpec> files;
		// Avoid describe -m for old servers JENKINS-48433
		if (!checkVersion(20141)) {
			files = getConnection().getChangelistFiles((int) id);
		} else {
			files = getConnection().getChangelistFiles((int) id, limit);
		}
		return files;
	}

	/**
	 * Find all files within a shelf.
	 *
	 * @param id Shelf ID
	 * @return List of file specs
	 * @throws Exception push up stack
	 */
	public List<IFileSpec> getShelvedFiles(int id) throws Exception {
		String cmd = CmdSpec.DESCRIBE.name();
		String[] args = new String[]{"-s", "-S", "" + id};
		List<Map<String, Object>> resultMaps;
		resultMaps = getConnection().execMapCmdList(cmd, args, null);

		List<IFileSpec> list = new ArrayList<>();

		if (resultMaps != null) {
			if ((!resultMaps.isEmpty()) && (resultMaps.get(0) != null)) {
				Map<String, Object> map = resultMaps.get(0);
				if (map.containsKey("shelved")) {
					for (int i = 0; map.get("rev" + i) != null; i++) {
						FileSpec fSpec = new FileSpec(map, getConnection(), i);
						fSpec.setChangelistId(id);
						list.add(fSpec);
					}
				}
			}
		}
		return list;
	}

	public String getSwarm() throws P4JavaException {
		GetPropertyOptions propOpts = new GetPropertyOptions();
		String key = "P4.Swarm.URL";
		propOpts.setName(key);
		List<IProperty> values = getConnection().getProperty(propOpts);
		for (IProperty prop : values) {
			if (key.equals(prop.getName())) {
				String url = prop.getValue();
				if (url != null && url.endsWith("/")) {
					url = url.substring(0, url.length() - 1);
				}
				return url;
			}
		}
		return null;
	}

	/**
	 * Get the latest change on the given path
	 *
	 * @param path Perforce depot path //foo/...
	 * @param from From revision (change or label)
	 * @param to   To revision (change or label)
	 * @return change number
	 * @throws Exception push up stack
	 */
	public long getHead(String path, P4Ref from, P4Ref to) throws Exception {
		String revisionPath = buildRevisionLimit(path, from, to);
		logger.info("getHead: p4 changes " + revisionPath);
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(revisionPath);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(1);
		List<IChangelistSummary> changes = getConnection().getChangelists(spec, opts);

		if (!changes.isEmpty()) {
			return changes.get(0).getId();
		}
		return -1;
	}

	/**
	 * Build a revision limit spec.
	 *
	 * @param path Perforce depot or client path //foo/...
	 * @param from From revision (change or label)
	 * @param to   To revision (change or label)
	 * @return a Perforce Revision Spec
	 */
	protected String buildRevisionLimit(String path, P4Ref from, P4Ref to) {
		String revisionPath = path;
		if (from != null && to != null) {
			revisionPath = revisionPath + "@" + from + "," + to;
		} else if (from == null && to != null) {
			revisionPath = revisionPath + "@" + to;
		} else if (from != null && to == null) {
			revisionPath = revisionPath + "@" + from + ",now";
		}
		return revisionPath;
	}

	/**
	 * Get the lowest change on the given path
	 *
	 * @param path Perforce depot path //foo/...
	 * @param from revision specifier
	 * @param to   revision specifier
	 * @return change number
	 * @throws Exception push up stack
	 */
	public long getLowestHead(String path, P4Ref from, P4Ref to) throws Exception {
		String revisionPath = buildRevisionLimit(path, from, to);
		logger.info("getLowestHead: p4 changes " + revisionPath);
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(revisionPath);

		List<IChangelistSummary> changes = getConnection().getChangelists(spec, null);
		if (!changes.isEmpty()) {
			return changes.get(changes.size() - 1).getId();
		}

		return -1;
	}

	public boolean hasFile(String depotPath) throws Exception {
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(depotPath);
		GetDepotFilesOptions opts = new GetDepotFilesOptions("-e");
		List<IFileSpec> specs = getConnection().getDepotFiles(files, opts);
		return getValidate().checkCatch(specs, "");
	}

	public ICommit getGraphCommit(String sha, String repo) throws P4JavaException {
		return getConnection().getCommitObject(sha, repo);
	}

	public List<IFileSpec> getCommitFiles(String repo, String sha) throws P4JavaException {
		return getConnection().getCommitFiles(repo, sha);
	}

	/**
	 * Get the last SHA commited to the specified repo.
	 *
	 * @param repo a graph repo
	 * @return a P4Ref of the last commit
	 */
	public P4Ref getGraphHead(String repo) {
		GraphCommitLogOptions opts = new GraphCommitLogOptions();
		opts.setMaxResults(1);
		opts.setRepo(repo);
		List<ICommit> list = null;
		try {
			list = getConnection().getGraphCommitLogList(opts);
		} catch (P4JavaException e) {
			log("P4: no commits under " + repo + " using HEAD.");
			return new P4LabelRef("HEAD");
		}

		if (!list.isEmpty() && list.get(0) != null) {
			ICommit commit = list.get(0);
			return new P4GraphRef(repo, commit);
		} else {
			log("P4: commit log empty for " + repo + " using HEAD.");
		}
		return new P4LabelRef("HEAD");
	}

	/**
	 * List graph Commits with in range of SHAs
	 *
	 * @param fromRefs Array of potential SHAs (include other repos)
	 * @param to       The last SHA to list commits
	 * @return a List or Commits
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listCommits(List<P4Ref> fromRefs, P4Ref to) throws Exception {
		List<P4Ref> list = new ArrayList<>();

		if (!(to instanceof P4GraphRef toGraph)) {
			return list;
		}

		for (P4Ref from : fromRefs) {
			if (!(from instanceof P4GraphRef fromGraph)) {
				continue;
			}

			// skip mismatched repos
			if (!fromGraph.getRepo().equals(toGraph.getRepo())) {
				continue;
			}

			// skip matching SHAs
			if (fromGraph.getSha().equals(toGraph.getSha())) {
				continue;
			}

			GraphCommitLogOptions opts = new GraphCommitLogOptions();
			String repo = fromGraph.getRepo();
			opts.setRepo(repo);
			String range = fromGraph.getSha() + ".." + toGraph.getSha();
			opts.setCommitValue(range);
			opts.setMaxResults(getMaxChangeLimit());

			List<ICommit> logList = getConnection().getGraphCommitLogList(opts);

			for (ICommit log : logList) {
				P4Ref ref = new P4GraphRef(repo, log);
				list.add(ref);
			}
		}
		return list;
	}

	/**
	 * List all Graph Repos
	 *
	 * @return A list of Graph Repos
	 * @throws Exception push up stack
	 */
	public List<IRepo> listAllRepos() throws Exception {
		List<IRepo> repos = getConnection().getRepos();
		return repos;
	}

	/**
	 * List of Graph Repos based on a path
	 *
	 * @param path Path filter
	 * @return A list of Graph Repos
	 * @throws Exception push up stack
	 */
	public List<IRepo> listRepos(String path) throws Exception {
		ReposOptions opts = new ReposOptions();
		opts.setNameFilter(path);
		List<IRepo> repos = getConnection().getRepos(opts);
		return repos;
	}

	protected int getMaxChangeLimit() {
		PerforceScm.DescriptorImpl scm = getP4SCM();
		int max = 0;
		if (scm != null) {
			max = scm.getMaxChanges();
		}
		max = (max >= 0) ? max : PerforceScm.DEFAULT_CHANGE_LIMIT;
		return max;
	}

	public long getHeadLimit() {
		PerforceScm.DescriptorImpl scm = getP4SCM();
		if (scm != null) {
			return scm.getHeadLimit();
		}
		return 0;
	}

	@Override
	public void close() {
		disconnect();
	}
}
