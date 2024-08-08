package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary.IClientOptions;
import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.InputMapper;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.option.changelist.SubmitOptions;
import com.perforce.p4java.option.client.AddFilesOptions;
import com.perforce.p4java.option.client.ParallelSyncOptions;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.client.ResolveFilesAutoOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.server.ChangelistOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.option.server.OpenedFilesOptions;
import com.perforce.p4java.server.CmdSpec;
import com.perforce.p4java.server.IServerInfo;
import hudson.AbortException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.changes.P4LabelRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.CheckOnlyImpl;
import org.jenkinsci.plugins.p4.populate.FlushOnlyImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.ParallelSync;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.jenkinsci.plugins.p4.publish.CommitImpl;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.tasks.TimeTask;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.jenkinsci.plugins.p4.console.P4ConsoleAnnotator.COMMAND;
import static org.jenkinsci.plugins.p4.console.P4ConsoleAnnotator.STOP;

public class ClientHelper extends ConnectionHelper {

	private static Logger logger = Logger.getLogger(ClientHelper.class.getName());

	private IClient iclient;

	public ClientHelper(ItemGroup context, String credential, TaskListener listener, Workspace workspace) throws IOException {
		super(context, credential, listener);
		clientLogin(workspace);
	}

	public ClientHelper(Item context, String credential, TaskListener listener, Workspace workspace) throws IOException {
		super(context, credential, listener);
		clientLogin(workspace);
	}

	public ClientHelper(P4BaseCredentials credential, TaskListener listener, Workspace workspace) throws IOException {
		super(credential, listener);
		clientLogin(workspace);
	}

	// reserved for TempClientHelper
	protected ClientHelper(Item context, String credential, TaskListener listener) throws IOException {
		super(context, credential, listener);
	}

	protected void clientLogin(Workspace workspace) throws AbortException {
		// Exit early if no connection
		if (getConnection() == null) {
			return;
		}

		// Find workspace and set as current
		try {

			// Setup charset for unicode servers
			if (isUnicode()) {
				getConnection().setCharsetName(workspace.getCharset());
			}

			// Setup/Create workspace based on type
			String user = getAuthorisationConfig().getUsername();
			try {
				login();
				iclient = workspace.setClient(getConnection(), user);
			} catch (RequestException | AccessException e) {
				invalidateSession();
				login();
				iclient = workspace.setClient(getConnection(), user);
			}

			// Set as Current Client, or exit early if not defined
			if (!isClientValid(workspace)) {
				String err = "P4: Undefined workspace: " + workspace.getFullName();
				throw new AbortException(err);
			} else {
				getConnection().setCurrentClient(iclient);
			}

			// Exit early if client is Static Workspace
			if (workspace instanceof StaticWorkspaceImpl) {
				return;
			}

			// Ensure root and host fields are not null
			if (workspace.getRootPath() != null) {
				iclient.setRoot(workspace.getRootPath());
			}
			if (workspace.getHostName() != null) {
				iclient.setHostName(workspace.getHostName());
			}

			// Set client Server ID if not already defined in the client spec.
			String serverId = iclient.getServerId();
			if (serverId == null || serverId.isEmpty()) {
				IServerInfo info = getConnection().getServerInfo();
				String services = getServerServices();
				serverId = info.getServerId();
				if (serverId != null && !serverId.isEmpty() && isEdgeType(services)) {
					iclient.setServerId(serverId);
				}
			}

			// Save client spec
			updateClient();
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			PrintWriter printWriter = new PrintWriter(writer);
			e.printStackTrace(printWriter);
			String err = "P4: Unable to setup workspace: " + writer.toString();
			logger.severe(err);
			log(err);
			throw new AbortException(e.getMessage());
		}
	}

	private void updateClient() throws Exception {

		// exit early if no change
		String clientName = iclient.getName();
		IClient original = getConnection().getClient(clientName);
		if (diffClient(original, iclient)) {
			return;
		}

		iclient.update();
		ClientView clientView = iclient.getClientView();

		// Log client view...
		if (clientView != null) {
			StringBuffer sb = new StringBuffer("...   View:\n");
			for (IClientViewMapping view : clientView) {
				sb.append("      ");
				if (view.getType() != null)
					sb.append(view.getType().toString());
				sb.append(view.getLeft());
				sb.append(" ");
				sb.append(view.getRight());
				sb.append("\n");
			}
			if (iclient.getStream() != null) {
				sb.append("...   Stream: " + iclient.getStream());
				sb.append("\n");
			}
			if (iclient.getStreamAtChange() > 0) {
				sb.append("...   Stream at change: " + iclient.getStreamAtChange());
				sb.append("\n");
			}
			sb.append("...   Root: " + iclient.getRoot());
			sb.append("\n");
			logger.finer(sb.toString());

			sb.insert(0, COMMAND);
			sb.append(STOP);
			log(sb.toString());
		}
	}

	private boolean diffClient(IClient a, IClient b) {

		if (a == null || b == null) {
			return false;
		}

		Map<String, Object> mapA = InputMapper.map(a);
		List<String> valuesA = cleanMap(mapA);

		Map<String, Object> mapB = InputMapper.map(b);
		List<String> valuesB = cleanMap(mapB);

		return valuesA.equals(valuesB);
	}

	private List<String> cleanMap(Map<String, Object> map) {

		// remove empty fields
		String[] unset = new String[]{"Host", "Stream"};
		for (String key : unset) {
			if ("".equals(map.get(key))) {
				map.remove(key);
			}
		}

		// remove set fields
		String[] set = new String[]{"Type"};
		for (String key : set) {
			map.remove(key);
		}

		// convert int fields
		String[] intFields = new String[]{"StreamAtChange"};
		for (String key : intFields) {
			Object value=map.remove(key);
			if (value != null) {
				map.put(key, value.toString());
			}
		}


		List<String> values = new ArrayList(map.values());
		values.removeAll(Collections.singleton(null));
		values.removeAll(Collections.singleton(""));
		Collections.sort(values);

		return values;
	}

	private boolean isEdgeType(String services) {
		if (services == null || services.isEmpty()) {
			return false;
		}
		if (services.contains("edge-server")) {
			return true;
		}
		if (services.contains("workspace-server")) {
			return true;
		}
		if (services.contains("build-server")) {
			return true;
		}
		return false;
	}

	// TODO remove when P4Java has support for 'serverServices'
	private String getServerServices() throws ConnectionException, AccessException {
		List<Map<String, Object>> mapList = getConnection().execMapCmdList(CmdSpec.INFO, new String[]{}, null);
		for (Map<String, Object> map : mapList) {
			if (map.containsKey("serverServices")) {
				return (String) map.get("serverServices");
			}
		}
		return "";
	}

	/**
	 * Sync files to workspace at the specified change/label.
	 *
	 * @param buildChange Change to sync from
	 * @param populate    Populate strategy
	 * @throws Exception push up stack
	 */
	public void syncFiles(P4Ref buildChange, Populate populate) throws Exception {
		TimeTask timer = new TimeTask();

		// test label is valid
		if (buildChange.isLabel()) {
			String label = buildChange.toString();
			try {
				int change = Integer.parseInt(label);
				log("P4 Task: label is a number! syncing files at change: " + change);
			} catch (NumberFormatException e) {
				if (!label.equals("now") && !isLabel(label) && !isClient(label) && !isCounter(label)) {
					String msg = "P4: Unable to find client/label/counter: " + label;
					log(msg);
					logger.warning(msg);
					throw new AbortException(msg);
				} else {
					log("P4 Task: syncing files at client/label: " + label);
				}
			}
		} else {
			log("P4 Task: syncing files at change: " + buildChange);
		}

		// Sync changes/labels
		if (buildChange instanceof P4ChangeRef || buildChange instanceof P4LabelRef) {
			// build file revision spec
			String path = iclient.getRoot() + "/...";
			String revisions = path + "@" + buildChange;

			// Sync files
			if (populate instanceof CheckOnlyImpl) {
				syncPreview(revisions, populate);
			} else if (populate instanceof FlushOnlyImpl) {
				syncHaveList(revisions, populate);
			} else {
				syncFiles(revisions, populate);
			}
		}

		// Sync graph repos
		if (buildChange instanceof P4GraphRef && populate instanceof GraphHybridImpl) {
			String rev = ((P4GraphRef) buildChange).getRepo() + "/...";
			syncFiles(rev, populate);
		}

		log("duration: " + timer.toString() + "\n");
	}

	/**
	 * Preview a sync, no have list update and no files
	 * <p>
	 * p4 sync -n ...
	 *
	 * @param revisions Perforce path and revision
	 * @param populate  Populate options
	 * @throws Exception
	 */
	private void syncPreview(String revisions, Populate populate) throws Exception {
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setNoUpdate(true);

		// Skip `p4 sync -q -n` to save compute time.
		if (populate.isQuiet()) {
			log("P4 Task: skipping sync.");
			return;
		}

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisions);
		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		getValidate().check(syncMsg, "file(s) up-to-date.", "file does not exist", "no file(s) as of that date");
	}

	/**
	 * Populate the have list, but no files.
	 * <p>
	 * p4 sync -k (p4 flush)
	 *
	 * @param revisions Perforce path and revision
	 * @param populate  Populate options
	 * @throws Exception
	 */
	private void syncHaveList(String revisions, Populate populate) throws Exception {
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setClientBypass(true);
		syncOpts.setQuiet(populate.isQuiet());

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisions);
		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		getValidate().check(syncMsg, "file(s) up-to-date.", "file does not exist", "no file(s) as of that date");
	}

	/**
	 * Sync files with various populate options.
	 *
	 * @param revisions Perforce path and revision
	 * @param populate  Populate options
	 * @throws Exception
	 */
	private void syncFiles(String revisions, Populate populate) throws Exception {

		// set MODTIME if populate options is used only required before 15.1
		if (populate.isModtime() && !checkVersion(20151)) {
			IClientOptions options = iclient.getOptions();
			if (!options.isModtime()) {
				options.setModtime(true);
				iclient.setOptions(options);
				// Save client spec
				updateClient();
			}
		}

		// sync options
		SyncOptions syncOpts = new SyncOptions();

		// setServerBypass (-p no have list)
		syncOpts.setServerBypass(!populate.isHave());

		// setForceUpdate (-f only if no -p is set)
		syncOpts.setForceUpdate(populate.isForce() && populate.isHave());
		syncOpts.setQuiet(populate.isQuiet());

		// Sync files with asynchronous callback and parallel if enabled to
		SyncStreamingCallback callback = new SyncStreamingCallback(iclient.getServer(), getListener());
		synchronized (callback) {
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisions);

			ParallelSync parallel = populate.getParallel();
			if (parallel != null && parallel.isEnable()) {
				ParallelSyncOptions parallelOpts = parallel.getParallelOptions();
				iclient.syncParallel(files, syncOpts, callback, 0, parallelOpts);
			} else {
				iclient.sync(files, syncOpts, callback, 0);
			}

			while (!callback.isDone()) {
				callback.wait();
			}

			if (callback.isFail()) {
				throw new P4JavaException(callback.getException());
			}
		}
	}

	/**
	 * Cleans up the Perforce workspace after a previous build. Removes all
	 * pending and abandoned files (equivalent to 'p4 revert -w').
	 *
	 * @param populate Jelly populate options
	 * @throws Exception push up stack
	 */
	public void tidyWorkspace(Populate populate) throws Exception {
		// relies on workspace view for scope.
		log("");
		String path = iclient.getRoot() + "/...";

		if (populate instanceof AutoCleanImpl) {
			tidyAutoCleanImpl(path, populate);
		}

		if (populate instanceof ForceCleanImpl) {
			tidyForceSyncImpl(path, populate);
		}

		if (populate instanceof GraphHybridImpl) {
			tidyForceSyncImpl(path, populate);
		}

		if (populate instanceof SyncOnlyImpl) {
			tidySyncOnlyImpl(path, populate);
		}
	}

	private void tidySyncOnlyImpl(String path, Populate populate) throws Exception {
		SyncOnlyImpl syncOnly = (SyncOnlyImpl) populate;

		if (syncOnly.isRevert()) {
			tidyPending(path);
		}
	}

	private void tidyForceSyncImpl(String path, Populate populate) throws Exception {
		// remove all pending files within workspace
		tidyPending(path);

		// remove all versioned files (clean have list)
		String revisions = iclient.getRoot() + "/...#0";

		// Only use quiet populate option to insure a clean sync
		boolean quiet = populate.isQuiet();
		Populate clean = new AutoCleanImpl(false, false, false, false, quiet, null, null);
		syncFiles(revisions, clean);

		// remove all files from workspace
		String encodedRoot = iclient.getRoot().replace("+", "%2B");
		String root = URLDecoder.decode(encodedRoot, StandardCharsets.UTF_8);
		log("... rm -rf " + root);
		log("");
		silentlyForceDelete(root);
	}

	private void silentlyForceDelete(String root) throws IOException {
		try {
			FileUtils.forceDelete(new File(root));
		} catch (FileNotFoundException | NoSuchFileException ignored) {
			// ignore
		} catch (IOException alt) {
			Path pathToDelete = Paths.get(root);
			if (!Files.exists(pathToDelete)) {
				return;
			}

			log("Unable to delete, trying alternative method... " + alt.getLocalizedMessage());

			List<Path> pathsToDelete = Files.walk(pathToDelete)
					.sorted(Comparator.reverseOrder())
					.collect(Collectors.toList());
			boolean success = true;
			for (Path path : pathsToDelete) {
				try {
					path.toFile().setWritable(true);
					Files.deleteIfExists(path);
				} catch (IOException e) {
					success = false;
					log("Unable to delete path: " + path + " error: " + e.getLocalizedMessage());
					// continue to delete other paths
				}
			}

			if (!success) {
				throw new IOException("Unable to delete all files (see log for details).");
			}
		}
	}

	private void tidyAutoCleanImpl(String path, Populate populate) throws Exception {
		// remove all pending files within workspace
		tidyPending(path);

		// clean files within workspace
		tidyClean(populate, path);
	}

	private void tidyPending(String path) throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: reverting all pending and shelved revisions.");

		// revert all pending and shelved revisions
		RevertFilesOptions rOpts = new RevertFilesOptions();
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);
		List<IFileSpec> list = iclient.revertFiles(files, rOpts);
		getValidate().check(list, "not opened on this client", "Replica does not support this command");

		// check for added files and remove...
		log("... rm [abandoned files]");
		for (IFileSpec file : list) {
			if (file.getAction() == FileAction.ABANDONED) {
				// first check if we have the local path
				String local = file.getLocalPathString();
				if (local == null) {
					local = depotToLocal(file);
				}
				if (local != null) {
					File unlink = new File(local);
					boolean ok = unlink.delete();
					if (!ok) {
						log("Not able to delete: " + local);
					}
				}
			}
		}
		log("duration: " + timer.toString() + "\n");
	}

	private void tidyClean(Populate populate, String path) throws Exception {

		// Use old method if 'p4 clean' is not supported
		if (!checkVersion(20141)) {
			tidyRevisions(path, populate);
			return;
		}

		// Set options
		boolean delete = ((AutoCleanImpl) populate).isDelete();
		boolean replace = ((AutoCleanImpl) populate).isReplace();

		ReconcileFilesOptions cleanOpts = new ReconcileFilesOptions();
		cleanOpts.setUpdateWorkspace(true);
		cleanOpts.setUseWildcards(true);

		if (delete && !replace) {
			cleanOpts.setOutsideAdd(true);
		}
		if (replace && !delete) {
			cleanOpts.setOutsideEdit(true);
			cleanOpts.setRemoved(true);
		}
		if (!replace && !delete) {
			log("P4 Task: skipping clean, no options set.");
			return;
		}

		// set MODTIME if populate options is used and server supports flag
		if (populate.isModtime()) {
			if (checkVersion(20141)) {
				cleanOpts.setCheckModTime(true);
			} else {
				log("P4: Resolving files by MODTIME not supported (requires 2014.1 or above)");
			}
		}

		TimeTask timer = new TimeTask();
		log("P4 Task: cleaning workspace to match have list.");

		// Reconcile with asynchronous callback
		ReconcileStreamingCallback callback = new ReconcileStreamingCallback(iclient.getServer(), getListener());
		synchronized (callback) {
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);
			iclient.reconcileFiles(files, cleanOpts, callback, 0);

			while (!callback.isDone()) {
				callback.wait();
			}

			if (callback.isFail()) {
				throw new P4JavaException(callback.getException());
			}
		}

		log("duration: " + timer.toString() + "\n");
	}

	private void tidyRevisions(String path, Populate populate) throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: tidying workspace to match have list.");

		boolean delete = ((AutoCleanImpl) populate).isDelete();
		boolean replace = ((AutoCleanImpl) populate).isReplace();

		// check status - find all missing, changed or added files
		String[] base = {"-n", "-a", "-e", "-d", "-l", "-f"};
		List<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(base));

		String[] args = list.toArray(new String[list.size()]);
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions(args);

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);
		List<IFileSpec> status = iclient.reconcileFiles(files, statusOpts);
		getValidate().check(status, "also opened by", "no file(s) to reconcile", "must sync/resolve",
				"exclusive file already opened", "cannot submit from stream", "instead of", "empty, assuming text");

		// Add missing, modified or locked files to a list, and delete the
		// unversioned files.
		List<IFileSpec> update = new ArrayList<IFileSpec>();
		for (IFileSpec s : status) {
			if (s.getOpStatus() == FileSpecOpStatus.VALID) {
				String local = s.getLocalPathString();
				if (local == null) {
					local = depotToLocal(s);
				}
				switch (s.getAction()) {
					case ADD:
						if (local != null && delete) {
							File unlink = new File(local);
							boolean ok = unlink.delete();
							if (!ok) {
								log("Not able to delete: " + local);
							}
						}
						break;
					default:
						update.add(s);
						break;
				}
			} else {
				String msg = s.getStatusMessage();
				if (msg.contains("exclusive file already opened")) {
					String rev = msg.substring(0, msg.indexOf(" - can't "));
					IFileSpec spec = new FileSpec(rev);
					update.add(spec);
				}
			}
		}

		// Force sync missing and modified files
		if (!update.isEmpty() && replace) {
			SyncOptions syncOpts = new SyncOptions();
			syncOpts.setForceUpdate(true);
			syncOpts.setQuiet(populate.isQuiet());

			List<IFileSpec> syncMsg = iclient.sync(update, syncOpts);
			getValidate().check(syncMsg, "file(s) up-to-date.", "file does not exist");
		}
		log("duration: " + timer.toString() + "\n");
	}

	public void revertAllFiles(boolean virtual) throws Exception {
		String path = iclient.getRoot() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);

		// revert all pending and shelved revisions
		RevertFilesOptions rOpts = new RevertFilesOptions();
		rOpts.setNoClientRefresh(virtual);
		List<IFileSpec> list = iclient.revertFiles(files, rOpts);
		getValidate().check(list, "not opened on this client", "Replica does not support this command");
	}

	public void versionFile(String file, Publish publish, int ChangelistID, boolean submit) throws Exception {
		// build file revision spec
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(file);
		findChangeFiles(files, publish.isDelete(), publish.isModtime());

		// Exit early if no change
		if (!isOpened(files)) {
			return;
		}

		// create/append changelist with files
		IChangelist change = appendPendingChangeList(files, publish, ChangelistID);

		// submit changelist
		if (submit) {
			submitFiles(change, false);
		}
	}

	public boolean buildChange(Publish publish) throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: reconcile files to changelist.");

		List<IFileSpec> files;
		if (publish instanceof CommitImpl) {
			CommitImpl commit = (CommitImpl) publish;
			files = FileSpecBuilder.makeFileSpecList(commit.getFiles());
			AddFilesOptions opts = new AddFilesOptions();
			iclient.addFiles(files, opts);
		} else {
			// build file revision spec
			String clientBase = "//" + iclient.getName() + "/";

			List<String> paths = buildPaths(publish, clientBase);

			files = FileSpecBuilder.makeFileSpecList(paths);
			findChangeFiles(files, publish.isDelete(), publish.isModtime());
		}

		// Check if file is open
		boolean open = isOpened(files);

		log("duration: " + timer.toString() + "\n");
		return open;
	}

	private List<String> buildPaths(Publish publish, String clientBase) {
		List<String> list = new ArrayList<>();
		String rawPaths = publish.getPaths();

		if (rawPaths == null || rawPaths.isEmpty()) {
			list.add(clientBase + "...");
			return list;
		}

		String[] array = rawPaths.split("\n\\s*");
		for (String a : array) {
			if (a.startsWith("//")) {
				list.add(a);
			} else {
				list.add(clientBase + a);
			}
		}
		return list;
	}

	private void findChangeFiles(List<IFileSpec> files, boolean delete, boolean modtime) throws Exception {
		// cleanup pending changes (revert -k)
		RevertFilesOptions revertOpts = new RevertFilesOptions();
		revertOpts.setNoClientRefresh(true);
		List<IFileSpec> revertStat = iclient.revertFiles(files, revertOpts);
		getValidate().check(revertStat, "file(s) not opened on this client.");

		// flush client to populate have (sync -k)
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setClientBypass(true);
		List<IFileSpec> syncStat = iclient.sync(files, syncOpts);
		getValidate().check(syncStat, "file(s) up-to-date.", "no such file(s).");

		// check status - find all changes to files
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts.setCheckModTime(modtime);
		statusOpts.setUseWildcards(true);
		statusOpts.setOutsideAdd(true);
		statusOpts.setOutsideEdit(true);
		statusOpts.setRemoved(delete);
		if (checkVersion(20191)) {
			statusOpts.setFileType(true);
		}

		List<IFileSpec> status = iclient.reconcileFiles(files, statusOpts);
		getValidate().check(status, "No file(s) to reconcile", "- no file(s) to reconcile", "instead of", "empty, assuming text", "also opened by");
	}

	public String publishChange(Publish publish) throws Exception {
		String id = null;
		TimeTask timer = new TimeTask();
		log("P4 Task: publish files to Perforce.");

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		// create changelist and open files
		IChangelist change = appendPendingChangeList(files, publish, IChangelist.DEFAULT);

		// logging
		OpenedFilesOptions openOps = new OpenedFilesOptions();
		List<IFileSpec> open = iclient.openedFiles(files, openOps);
		for (IFileSpec f : open) {
			FileAction action = f.getAction();
			String path = f.getDepotPathString();
			log("... ... " + action + " " + path);
		}

		// if SUBMIT
		if (publish instanceof SubmitImpl) {
			SubmitImpl submit = (SubmitImpl) publish;
			boolean reopen = submit.isReopen();
			long c = submitFiles(change, reopen);
			id = Long.toString(c);
		}

		// if SHELVE
		if (publish instanceof ShelveImpl) {
			ShelveImpl shelve = (ShelveImpl) publish;
			boolean revert = shelve.isRevert();
			long c = shelveFiles(change, files, revert);
			id = Long.toString(c);
		}

		// if COMMIT
		if (publish instanceof CommitImpl) {
			CommitImpl commit = (CommitImpl) publish;
			id = commitFiles(change);
		}

		log("duration: " + timer.toString() + "\n");
		return id;
	}

	private IChangelist appendPendingChangeList(List<IFileSpec> files, Publish publish, int ChangeListID) throws Exception {

		String desc = publish.getExpandedDesc();

		if (ChangeListID == IChangelist.UNKNOWN || ChangeListID == IChangelist.DEFAULT) {

			// create new pending change and add description
			IChangelist change = new Changelist();
			change.setDescription(desc);
			change = iclient.createChangelist(change);
			ChangeListID = change.getId();
		}

		log("... pending change: " + ChangeListID);

		// move files from default change
		ReopenFilesOptions reopenOpts = new ReopenFilesOptions();
		reopenOpts.setChangelistId(ChangeListID);

		// set purge if required
		if (publish instanceof SubmitImpl) {
			SubmitImpl submit = (SubmitImpl) publish;
			int purge = submit.getPurgeValue();
			if (purge > 0) {
				reopenOpts.setFileType("+S" + purge);
			}
		}
		iclient.reopenFiles(files, reopenOpts);

		return getChange(ChangeListID);
	}

	private long submitFiles(IChangelist change, boolean reopen) throws Exception {
		log("... submitting files");

		SubmitOptions submitOpts = new SubmitOptions();
		submitOpts.setReOpen(reopen);

		// submit with asynchronous callback
		SubmitStreamingCallback callback = new SubmitStreamingCallback(iclient.getServer(), getListener());
		synchronized (callback) {
			change.submit(submitOpts, callback, 0);
			while (!callback.isDone()) {
				callback.wait();
			}
		}

		if (callback.isFail()) {
			throw new P4JavaException(callback.getException());
		}

		long cngNumber = callback.getChange();
		if (cngNumber > 0) {
			log("... submitted in change: " + cngNumber);
		} else {
			throw new P4JavaException("Unable to submit change.");
		}
		return cngNumber;
	}

	private String commitFiles(IChangelist change) throws Exception {
		log("... committing files");
		List<String> opts = new ArrayList<>();
		opts.add("-c");
		opts.add(String.valueOf(change.getId()));
		String[] args = opts.toArray(new String[opts.size()]);

		Map<String, Object>[] results = getConnection().execMapCmd(CmdSpec.SUBMIT.name(), args, null);
		for (Map<String, Object> map : results) {
			if (map.containsKey("submittedCommit")) {
				String sha = (String) map.get("submittedCommit");
				log("... committing SHA: " + sha);
				return sha;
			}
		}
		throw new P4JavaException("Unable to commit change.");
	}

	private long shelveFiles(IChangelist change, List<IFileSpec> files, boolean revert) throws Exception {
		log("... shelving files");

		List<IFileSpec> shelved = iclient.shelveChangelist(change);
		getValidate().check(shelved, "");

		// post shelf cleanup
		RevertFilesOptions revertOpts = new RevertFilesOptions();
		revertOpts.setChangelistId(change.getId());
		revertOpts.setNoClientRefresh(!revert);
		String r = (revert) ? "(revert)" : "(revert -k)";
		log("... reverting open files " + r);
		iclient.revertFiles(files, revertOpts);
		return change.getId();
	}

	private boolean isOpened(List<IFileSpec> files) throws Exception {
		OpenedFilesOptions openOps = new OpenedFilesOptions();
		List<IFileSpec> open = iclient.openedFiles(files, openOps);
		for (IFileSpec file : open) {
			if (file != null && file.getAction() != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Workaround for p4java bug. The 'setLocalSyntax(true)' option does not
	 * provide local syntax, so I have to use 'p4 where' to translate through
	 * the client view.
	 *
	 * @param fileSpec Perforce file spec
	 * @return Local syntax
	 * @throws Exception push up stack
	 */
	private String depotToLocal(IFileSpec fileSpec) throws Exception {
		String depotPath = fileSpec.getDepotPathString();
		if (depotPath == null) {
			depotPath = fileSpec.getOriginalPathString();
		}
		if (depotPath == null) {
			return null;
		}
		List<IFileSpec> dSpec = FileSpecBuilder.makeFileSpecList(depotPath);
		List<IFileSpec> lSpec = iclient.where(dSpec);
		String path = lSpec.get(0).getLocalPathString();
		return path;
	}

	private String localToDepot(IFileSpec fileSpec) throws Exception {
		String depotPath = fileSpec.getDepotPathString();
		if (depotPath == null) {
			depotPath = fileSpec.getOriginalPathString();
		}
		if (depotPath == null) {
			return null;
		}
		List<IFileSpec> dSpec = FileSpecBuilder.makeFileSpecList(depotPath);
		List<IFileSpec> lSpec = iclient.where(dSpec);
		String path = lSpec.get(0).getDepotPathString();
		return path;
	}

	private void deleteFile(String rev) throws Exception {
		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(rev);

		String local = depotToLocal(file.get(0));
		File unlink = new File(local);

		if (unlink.exists()) {
			boolean ok = unlink.delete();
			if (!ok) {
				log("Not able to delete: " + local);
			}
		}
	}

	private void printFile(String rev) throws Exception {
		byte[] buf = new byte[1024 * 64];

		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(rev);
		GetFileContentsOptions printOpts = new GetFileContentsOptions();
		printOpts.setNoHeaderLine(true);
		InputStream ins = getConnection().getFileContents(file, printOpts);

		String localPath = depotToLocal(file.get(0));
		File target = new File(localPath);

		// Create directories as required JENKINS-37868
		if (target.getParentFile().mkdirs()) {
			log("Directory created: " + target);
		}

		// Make writable if it exists
		if (target.exists()) {
			target.setWritable(true);
		}
		FileOutputStream outs = new FileOutputStream(target);
		BufferedOutputStream bouts = new BufferedOutputStream(outs);

		int len;
		while ((len = ins.read(buf)) > 0) {
			bouts.write(buf, 0, len);
		}
		ins.close();
		bouts.close();
	}

	/**
	 * Unshelve review into workspace. Workspace is sync'ed to head first then
	 * review unshelved.
	 *
	 * @param review Review number (perhaps long?)
	 * @throws Exception push up stack
	 */
	public void unshelveFiles(long review) throws Exception {
		// skip if review is 0 or less
		if (review < 1) {
			log("P4 Task: skipping review: " + review);
			return;
		}

		TimeTask timer = new TimeTask();
		log("P4 Task: unshelve review: " + review);

		// Unshelve change for review
		List<IFileSpec> shelveMsg;
		shelveMsg = iclient.unshelveChangelist((int) review, null, 0, true, false);
		getValidate().check(shelveMsg, false, "also opened by", "No such file(s)",
				"exclusive file already opened", "no file(s) to unshelve");

		// force sync any files missed due to INFO messages e.g. exclusive files
		for (IFileSpec spec : shelveMsg) {
			if (spec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = spec.getStatusMessage();
				if (msg.contains("exclusive file already opened")) {
					String rev = msg.substring(0, msg.indexOf(" - can't "));
					if (msg.contains("can't delete") || msg.contains("can't move/delete")) {
						// JENKINS-47141 delete workspace file manually when locked
						log("P4 Task: delete: " + rev);
						deleteFile(rev);
					} else {
						// JENKINS-37868 use '@= + review' for correct file
						log("P4 Task: print: " + rev);
						printFile(rev + "@=" + review);
					}
				}
			} else {
				log(spec.getDepotPathString());
			}
		}

		log("... duration: " + timer.toString());
	}

	/**
	 * Resolve files in workspace with the specified option.
	 *
	 * @param mode Resolve mode
	 * @throws Exception push up stack
	 */
	public void resolveFiles(String mode) throws Exception {

		if ("none".equals(mode)) {
			return;
		}

		TimeTask timer = new TimeTask();
		log("P4 Task: resolve: -" + mode);

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		files = FileSpecBuilder.makeFileSpecList(path);

		// Unshelve change for review
		ResolveFilesAutoOptions rsvOpts = new ResolveFilesAutoOptions();
		rsvOpts.setAcceptTheirs("at".equals(mode));
		rsvOpts.setAcceptYours("ay".equals(mode));
		rsvOpts.setSafeMerge("as".equals(mode));
		rsvOpts.setForceResolve("af".equals(mode));

		List<IFileSpec> rsvMsg = iclient.resolveFilesAuto(files, rsvOpts);
		getValidate().check(rsvMsg, "no file(s) to resolve");

		log("... duration: " + timer.toString());
	}

	/**
	 * Gets the Changelist (p4 describe -s); shouldn't need a client, but
	 * p4-java throws an exception if one is not set.
	 *
	 * @param id Change number (long perhaps)
	 * @return Perforce Changelist
	 * @throws Exception push up stack
	 */
	public Changelist getChange(long id) throws Exception {
		try {
			return (Changelist) getConnection().getChangelist((int) id);
		} catch (RequestException e) {
			ChangelistOptions opts = new ChangelistOptions();
			opts.setOriginalChangelist(true);
			return (Changelist) getConnection().getChangelist((int) id, opts);
		}
	}

	/**
	 * Get the change number for the last change within the scope of the
	 * workspace view up to the specified revision
	 *
	 * @param from From revision (change or label)
	 * @param to   To revision (change or label)
	 * @return Perforce change
	 * @throws Exception push up stack
	 */
	public long getClientHead(P4Ref from, P4Ref to) throws Exception {
		// build file revision spec
		String path = "//" + iclient.getName() + "/...";
		String revisionPath = buildRevisionLimit(path, from, to);

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisionPath);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setType(IChangelist.Type.SUBMITTED);
		opts.setMaxMostRecent(1);
		List<IChangelistSummary> list = getConnection().getChangelists(files, opts);

		if (!list.isEmpty() && list.get(0) != null) {
			long change = list.get(0).getId();
			log("P4: found " + change + " revision in " + revisionPath);
			return change;
		} else {
			log("P4: no revisions under " + revisionPath);
			return 0L;
		}
	}

	/**
	 * Get the change number for the last change within the scope of the
	 * workspace view. If there are no recent changes use the latest change.
	 *
	 * @return Perforce change
	 * @throws Exception push up stack
	 */
	public long getClientHead() throws Exception {
		// get last change in server, may return shelved CLs
		String latestChange = getConnection().getCounter("change");
		long latest = Long.parseLong(latestChange);
		P4Ref to = new P4ChangeRef(latest);
		long head = getClientHead(null, to);
		return (head == 0L) ? latest : head;
	}

	public List<IChangelistSummary> getPendingChangelists(boolean includeLongDescription, String clientName) throws Exception {

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setType(IChangelist.Type.PENDING);
		opts.setMaxMostRecent(getMaxChangeLimit());
		opts.setLongDesc(includeLongDescription);
		opts.setClientName(clientName);
		List<IChangelistSummary> list = getConnection().getChangelists(files, opts);

		// In-line implementation of comparator because of course you can't sort changelists....
		Collections.sort(list, new Comparator<IChangelistSummary>() {
			public int compare(IChangelistSummary one, IChangelistSummary two) {
				return Integer.compare(one.getId(), two.getId());
			}
		});
		Collections.reverse(list);

		return list;
	}

	public int findPendingChangelistIDByDesc(String desc, String client) throws Exception {
		// Find the changelist if it exists
		int changelistID = IChangelist.UNKNOWN;
		List<IChangelistSummary> ol = getPendingChangelists(true, client);
		for (IChangelistSummary item : ol) {
			logger.fine("P4: Checking Changelist: " + item.getId() + " [" + item.getDescription() + "]");
			// NOTE: For some reason when creating changelists p4java seems to spit a newline at the end of the description.
			if (item.getDescription().replaceAll("\\r\\n|\\r|\\n", "").trim().equalsIgnoreCase(desc.trim())) {
				changelistID = item.getId();
				break;
			}
		}
		return changelistID;
	}

	/**
	 * List of Graph Repos within the client's view
	 *
	 * @return A list of Graph Repos, empty list on error.
	 */
	public List<IRepo> listRepos() {
		List<IRepo> repos = new ArrayList<>();
		try {
			repos = iclient.getRepos();
		} catch (Exception e) {
			logger.fine("No repos found: " + e.getMessage());
		}
		return repos;
	}

	/**
	 * Show all changes within the scope of the client, between the 'from' and
	 * 'to' change limits.
	 *
	 * @param fromRefs list of from revisions (change or label)
	 * @param to       To revision (change or label)
	 * @return List of changes
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listChanges(List<P4Ref> fromRefs, P4Ref to) throws Exception {

		P4Ref from = getSingleChange(fromRefs);

		// return empty array, if from and to are equal, or Perforce will report
		// a change
		if (from.equals(to)) {
			return new ArrayList<P4Ref>();
		}

		// JENKINS-68516: skip changelist calculation if maxChanges=0.
		if (getMaxChangeLimit() <= 0) {
			return new ArrayList<P4Ref>();
		}

		String ws = "//" + iclient.getName() + "/...@" + from + "," + to;
		List<P4Ref> list = listChanges(ws);
		if (!from.isLabel()) {
			list.remove(from);
		}
		return list;
	}

	private P4Ref getSingleChange(List<P4Ref> refs) {
		// fetch single change and ignore commits
		for (P4Ref ref : refs) {
			if (!ref.isCommit()) {
				return ref;
			}
		}
		return null;
	}

	/**
	 * Show all changes within the scope of the client, from the 'from' change
	 * limits.
	 *
	 * @param from From revision (change or label)
	 * @return List of changes
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listChanges(P4Ref from) throws Exception {
		String ws = "//" + iclient.getName() + "/...@" + from + ",now";
		List<P4Ref> list = listChanges(ws);
		if (!from.isLabel()) {
			list.remove(from);
		}
		return list;
	}

	/**
	 * Show all changes within the scope of the client.
	 *
	 * @return List of changes
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listChanges() throws Exception {
		String ws = "//" + iclient.getName() + "/...";
		return listChanges(ws);
	}

	private List<P4Ref> listChanges(String ws) throws Exception {
		List<P4Ref> list = new ArrayList<P4Ref>();
		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(getMaxChangeLimit());

		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(ws);
		List<IChangelistSummary> cngs = getConnection().getChangelists(spec, opts);
		if (cngs != null) {
			for (IChangelistSummary c : cngs) {
				// don't try to add null or -1 changes
				if (c != null && c.getId() != -1) {
					P4Ref rev = new P4ChangeRef(c.getId());
					// don't add change entries already in the list
					if (!(list.contains(rev))) {
						list.add(rev);
					}
				}
			}
		}

		Collections.sort(list);
		Collections.reverse(list);
		return list;
	}

	/**
	 * Fetches a list of changes needed to update the workspace to head.
	 *
	 * @param fromRefs List from revisions
	 * @return List of changes
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listHaveChanges(List<P4Ref> fromRefs) throws Exception {
		P4Ref from = getSingleChange(fromRefs);
		if (from.getChange() > 0) {
			log("P4: Polling with range: " + from + ",now");
			return listChanges(from);
		}

		String path = "//" + iclient.getName() + "/...";
		return listHaveChanges(path);
	}

	/**
	 * Fetches a list of changes needed to update the workspace to the specified
	 * limit. The limit could be a Perforce change number, label or counter.
	 *
	 * @param fromRefs    List of from revisions
	 * @param changeLimit To Revision
	 * @return List of changes
	 * @throws Exception push up stack
	 */
	public List<P4Ref> listHaveChanges(List<P4Ref> fromRefs, P4Ref changeLimit) throws Exception {

		P4Ref from = getSingleChange(fromRefs);
		// convert changeLimit from counter to change value
		if (isCounter(changeLimit.toString())) {
			changeLimit = new P4ChangeRef(Long.parseLong(getCounter(changeLimit.toString())));
		}
		// return empty array, if from and changeLimit are equal, or Perforce will report a change
		if (from.equals(changeLimit)) {
			return new ArrayList<P4Ref>();
		}

		if (from.getChange() > 0) {
			log("P4: Polling with range: " + from + "," + changeLimit);
			return listChanges(fromRefs, changeLimit);
		}

		String path = "//" + iclient.getName() + "/...";
		String fileSpec = path + "@" + changeLimit;
		return listHaveChanges(fileSpec);
	}

	private List<P4Ref> listHaveChanges(String fileSpec) throws Exception {
		log("P4: Polling with cstat: " + fileSpec);

		List<P4Ref> haveChanges = new ArrayList<P4Ref>();
		Map<String, Object>[] map;
		map = getConnection().execMapCmd("cstat", new String[]{fileSpec}, null);

		for (Map<String, Object> entry : map) {
			String status = (String) entry.get("status");
			if (status != null) {
				if (status.startsWith("have")) {
					String value = (String) entry.get("change");
					int change = Integer.parseInt(value);
					haveChanges.add(new P4ChangeRef(change));
				}
			}
		}
		Collections.sort(haveChanges);
		Collections.reverse(haveChanges);
		return haveChanges;
	}

	public ClientView getClientView() {
		return iclient.getClientView();
	}

	public boolean isClientValid(Workspace workspace) {
		if (iclient == null) {
			String msg;
			if (workspace instanceof TemplateWorkspaceImpl) {
				TemplateWorkspaceImpl template = ((TemplateWorkspaceImpl) workspace);
				String name = template.getTemplateName();
				msg = "P4: Template workspace not found: " + name;
			} else {
				String name = workspace.getFullName();
				msg = "P4: Unable to use workspace: " + name;
			}
			logger.severe(msg);
			if (getListener() != null) {
				log(msg);
			}
			return false;
		}
		return true;
	}

	public IClient getClient() {
		return iclient;
	}

	public String where(String localFile) throws Exception {
		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(localFile);
		return localToDepot(file.get(0));
	}
}
