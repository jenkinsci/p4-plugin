package org.jenkinsci.plugins.p4.client;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.CheckOnlyImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.ParallelSync;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.tasks.TimeTask;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary.IClientOptions;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.option.changelist.SubmitOptions;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.client.ResolveFilesAutoOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.option.server.OpenedFilesOptions;

import hudson.AbortException;
import hudson.model.TaskListener;

public class ClientHelper extends ConnectionHelper {

	private static Logger logger = Logger.getLogger(ClientHelper.class.getName());

	private final Validate validate;

	private IClient iclient;

	public ClientHelper(String credential, TaskListener listener, String client, String charset) {
		super(credential, listener);
		clientLogin(client, charset);
		validate = new Validate(listener);
	}

	public ClientHelper(P4BaseCredentials credential, TaskListener listener, String client, String charset) {
		super(credential, listener);
		clientLogin(client, charset);
		validate = new Validate(listener);
	}

	private void clientLogin(String client, String charset) {
		// Exit early if no connection
		if (connection == null) {
			return;
		}

		// Find workspace and set as current
		try {
			iclient = connection.getClient(client);
			connection.setCurrentClient(iclient);
		} catch (Exception e) {
			String err = "P4: Unable to use Workspace: " + e;
			logger.severe(err);
			log(err);
			e.printStackTrace();
		}

		if (isUnicode()) {
			connection.setCharsetName(charset);
		}

	}

	public void setClient(Workspace workspace) throws Exception {

		// Setup/Create workspace based on type
		iclient = workspace.setClient(connection, authorisationConfig.getUsername());

		// Exit early if client is not defined
		if (!isClientValid(workspace)) {
			String err = "P4: Undefined workspace: " + workspace.getFullName();
			throw new AbortException(err);
		}

		// Exit early if client is Static
		if (workspace instanceof StaticWorkspaceImpl) {
			connection.setCurrentClient(iclient);
			return;
		}

		// Ensure root and host fields are not null
		if (workspace.getRootPath() != null) {
			iclient.setRoot(workspace.getRootPath());
		}
		if (workspace.getHostName() != null) {
			iclient.setHostName(workspace.getHostName());
		}

		// Set clobber on to ensure workspace is always good
		IClientOptions options = iclient.getOptions();
		options.setClobber(true);
		iclient.setOptions(options);

		// Save client spec
		iclient.update();

		// Set active client for this connection
		connection.setCurrentClient(iclient);
		return;
	}

	/**
	 * Sync files to workspace at the specified change/label.
	 *
	 * @param buildChange
	 *            Change to sync from
	 * @param populate
	 *            Populate strategy
	 * @throws Exception
	 */
	public void syncFiles(P4Revision buildChange, Populate populate) throws Exception {
		TimeTask timer = new TimeTask();

		// test label is valid
		if (buildChange.isLabel()) {
			String label = buildChange.toString();
			try {
				int change = Integer.parseInt(label);
				log("P4 Task: label is a number! syncing files at change: " + change);
			} catch (NumberFormatException e) {
				if (!isLabel(label) && !isClient(label)) {
					String msg = "P4: Unable to find client/label: " + label;
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

		// build file revision spec
		String path = iclient.getRoot() + "/...";
		String revisions = path + "@" + buildChange;

		// Sync files
		if (populate instanceof CheckOnlyImpl) {
			syncHaveList(revisions, populate);
		} else {
			syncFiles(revisions, populate);
		}

		// Save buildChange in client Description.
		buildChange.save(iclient);

		log("duration: " + timer.toString() + "\n");
	}

	/**
	 * Test to see if workspace is at the latest revision.
	 *
	 * @throws Exception
	 */
	private boolean syncHaveList(String revisions, Populate populate) throws Exception {
		// Preview (sync -k)
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setClientBypass(true);
		syncOpts.setQuiet(populate.isQuiet());

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisions);
		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		validate.check(syncMsg, "file(s) up-to-date.", "file does not exist", "no file(s) as of that date");

		for (IFileSpec fileSpec : syncMsg) {
			if (fileSpec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = fileSpec.getStatusMessage();
				if (msg.contains("file(s) up-to-date.")) {
					return true;
				}
			}
		}
		return false;
	}

	private void syncFiles(String revisions, Populate populate) throws Exception {

		// set MODTIME if populate options is used only required before 15.1
		if (populate.isModtime() && !checkVersion(20151)) {
			IClientOptions options = iclient.getOptions();
			if (!options.isModtime()) {
				options.setModtime(true);
				iclient.setOptions(options);
				iclient.update(); // Save client spec
			}
		}

		// sync options
		SyncOptions syncOpts = new SyncOptions();

		// setServerBypass (-p no have list)
		syncOpts.setServerBypass(!populate.isHave());

		// setForceUpdate (-f only if no -p is set)
		syncOpts.setForceUpdate(populate.isForce() && populate.isHave());
		syncOpts.setQuiet(populate.isQuiet());

		// Check if we need to use the native p4 and not p4java
		ParallelSync parallel = populate.getParallel();
		if (parallel != null && parallel.isEnable()) {
			int exitCode = CheckNativeUse(revisions, syncOpts, parallel);
			if (exitCode == 0) {
				return;
			}
		}

		// fall back to asynchronous callback
		SyncStreamingCallback callback = new SyncStreamingCallback(iclient.getServer(), listener);
		synchronized (callback) {
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(revisions);
			iclient.sync(files, syncOpts, callback, 0);
			while (!callback.isDone()) {
				callback.wait();
			}
		}
	}

	private int CheckNativeUse(String revisions, SyncOptions syncOpts, ParallelSync parallel)
			throws AccessException, RequestException, ConnectionException, IOException {

		try {
			String p4 = parallel.getPath();

			List<String> command = new ArrayList<String>();
			command.add(p4);
			command.add("-c" + iclient.getName());
			command.add("-p" + p4credential.getP4port());
			command.add("-u" + p4credential.getUsername());

			command.add("sync");
			if (syncOpts.isForceUpdate()) {
				command.add("-f");
			}
			if (syncOpts.isQuiet()) {
				command.add("-q");
			}
			if (syncOpts.isClientBypass()) {
				command.add("-k");
			}
			if (syncOpts.isSafetyCheck()) {
				command.add("-s");
			}
			if (syncOpts.isServerBypass()) {
				command.add("-p");
			}
			if (syncOpts.isNoUpdate()) {
				command.add("-n");
			}

			String threads = parallel.getThreads();
			String minfiles = parallel.getMinfiles();
			String minbytes = parallel.getMinbytes();
			command.add("--parallel");
			command.add("threads=" + threads + ",min=" + minfiles + ",minsize=" + minbytes);

			command.add(revisions);

			ProcessBuilder builder = new ProcessBuilder(command);
			final Process process = builder.start();
			InputStream inputStream = process.getInputStream();
			InputStream errorStream = process.getErrorStream();

			BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
			BufferedReader errorStreamReader = new BufferedReader(new InputStreamReader(errorStream));

			// Log commands
			log("(p4):cmd:... " + StringUtils.join(command, " "));
			log("");

			String line;
			while ((line = inputStreamReader.readLine()) != null) {
				log(line);
			}
			while ((line = errorStreamReader.readLine()) != null) {
				log(line);
			}
			int exitCode = process.waitFor();

			log("exitCode=" + Integer.toString(exitCode));
			log("(p4):stop:0");
			return exitCode;
		} catch (Exception e) {
			log(e.getMessage());
			return 1;
		}
	}

	/**
	 * Cleans up the Perforce workspace after a previous build. Removes all
	 * pending and abandoned files (equivalent to 'p4 revert -w').
	 *
	 * @throws Exception
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
		Populate clean = new AutoCleanImpl(false, false, false, quiet, null, null);
		syncFiles(revisions, clean);

		// remove all files from workspace
		String root = iclient.getRoot();
		log("... rm -rf " + root);
		log("");
		silentlyForceDelete(root);
	}

	private void silentlyForceDelete(String root) throws IOException {
		try {
			FileUtils.forceDelete(new File(root));
		} catch (FileNotFoundException ignored) {

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
		validate.check(list, "not opened on this client");

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
					unlink.delete();
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

		String[] base = { "-w", "-f" };
		List<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(base));

		if (delete && !replace) {
			list.add("-a");
		}
		if (replace && !delete) {
			list.add("-e");
			list.add("-d");
		}
		if (!replace && !delete) {
			log("P4 Task: skipping clean, no options set.");
			return;
		}

		// set MODTIME if populate options is used and server supports flag
		if (populate.isModtime()) {
			if (checkVersion(20141)) {
				list.add("-m");
			} else {
				log("P4: Resolving files by MODTIME not supported (requires 2014.1 or above)");
			}
		}

		TimeTask timer = new TimeTask();
		log("P4 Task: cleaning workspace to match have list.");

		String[] args = list.toArray(new String[list.size()]);
		ReconcileFilesOptions cleanOpts = new ReconcileFilesOptions(args);

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);
		List<IFileSpec> status = iclient.reconcileFiles(files, cleanOpts);
		validate.check(status, "also opened by", "no file(s) to reconcile", "must sync/resolve",
				"exclusive file already opened", "cannot submit from stream", "instead of", "empty, assuming text");

		log("duration: " + timer.toString() + "\n");
	}

	private void tidyRevisions(String path, Populate populate) throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: tidying workspace to match have list.");

		boolean delete = ((AutoCleanImpl) populate).isDelete();
		boolean replace = ((AutoCleanImpl) populate).isReplace();

		// check status - find all missing, changed or added files
		String[] base = { "-n", "-a", "-e", "-d", "-l", "-f" };
		List<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(base));

		String[] args = list.toArray(new String[list.size()]);
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions(args);

		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);
		List<IFileSpec> status = iclient.reconcileFiles(files, statusOpts);
		validate.check(status, "also opened by", "no file(s) to reconcile", "must sync/resolve",
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
						unlink.delete();
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
			validate.check(syncMsg, "file(s) up-to-date.", "file does not exist");
		}
		log("duration: " + timer.toString() + "\n");
	}

	public void revertAllFiles() throws Exception {
		String path = iclient.getRoot() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(path);

		// revert all pending and shelved revisions
		RevertFilesOptions rOpts = new RevertFilesOptions();
		List<IFileSpec> list = iclient.revertFiles(files, rOpts);
		validate.check(list, "not opened on this client");
	}

	public void versionFile(String file, String desc) throws Exception {
		// build file revision spec
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(file);
		findChangeFiles(files);

		// Exit early if no change
		if (!isOpened(files)) {
			return;
		}

		// create changelist with files
		IChangelist change = createChangeList(files, desc);

		// submit changelist
		submitFiles(change, false);
	}

	public boolean buildChange() throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: reconcile files to changelist.");

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);
		findChangeFiles(files);

		// Check if file is open
		boolean open = isOpened(files);

		log("duration: " + timer.toString() + "\n");
		return open;
	}

	private void findChangeFiles(List<IFileSpec> files) throws Exception {
		// cleanup pending changes (revert -k)
		RevertFilesOptions revertOpts = new RevertFilesOptions();
		revertOpts.setNoClientRefresh(true);
		List<IFileSpec> revertStat = iclient.revertFiles(files, revertOpts);
		validate.check(revertStat, "");

		// flush client to populate have (sync -k)
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setClientBypass(true);
		List<IFileSpec> syncStat = iclient.sync(files, syncOpts);
		validate.check(syncStat, "file(s) up-to-date.");

		// check status - find all changes to files
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts.setUseWildcards(true);
		statusOpts.setOutsideAdd(true);
		statusOpts.setOutsideEdit(true);

		List<IFileSpec> status = iclient.reconcileFiles(files, statusOpts);
		validate.check(status, "- no file(s) to reconcile", "instead of", "empty, assuming text", "also opened by");
	}

	public void publishChange(Publish publish) throws Exception {
		TimeTask timer = new TimeTask();
		log("P4 Task: publish files to Perforce.");

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);
		String desc = publish.getExpandedDesc();

		IChangelist change = createChangeList(files, desc);

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
			submitFiles(change, reopen);
		}

		// if SHELVE
		if (publish instanceof ShelveImpl) {
			ShelveImpl shelve = (ShelveImpl) publish;
			boolean revert = shelve.isRevert();
			shelveFiles(change, files, revert);
		}

		log("duration: " + timer.toString() + "\n");
	}

	private IChangelist createChangeList(List<IFileSpec> files, String desc) throws Exception {
		// create new pending change and add description
		IChangelist change = new Changelist();
		change.setDescription(desc);
		change = iclient.createChangelist(change);
		log("... pending change: " + change.getId());

		// move files from default change
		ReopenFilesOptions reopenOpts = new ReopenFilesOptions();
		reopenOpts.setChangelistId(change.getId());
		iclient.reopenFiles(files, reopenOpts);

		return change;
	}

	private void submitFiles(IChangelist change, boolean reopen) throws Exception {
		log("... submitting files");

		SubmitOptions submitOpts = new SubmitOptions();
		submitOpts.setReOpen(reopen);

		List<IFileSpec> submitted = change.submit(submitOpts);
		validate.check(submitted, "Submitted as change");

		long cngNumber = findSubmittedChange(submitted);
		if (cngNumber > 0) {
			log("... submitted in change: " + cngNumber);
		}
	}

	private void shelveFiles(IChangelist change, List<IFileSpec> files, boolean revert) throws Exception {
		log("... shelving files");

		List<IFileSpec> shelved = iclient.shelveChangelist(change);
		validate.check(shelved, "");

		// post shelf cleanup
		RevertFilesOptions revertOpts = new RevertFilesOptions();
		revertOpts.setChangelistId(change.getId());
		revertOpts.setNoClientRefresh(!revert);
		String r = (revert) ? "(revert)" : "(revert -k)";
		log("... reverting open files " + r);
		iclient.revertFiles(files, revertOpts);
	}

	private long findSubmittedChange(List<IFileSpec> submitted) {
		long change = 0;
		for (IFileSpec spec : submitted) {
			if (spec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = spec.getStatusMessage();
				String cng = "Submitted as change ";
				if (msg.startsWith(cng)) {
					try {
						String id = msg.substring(cng.length());
						change = Long.parseLong(id);
					} catch (NumberFormatException e) {
						change = -1;
					}
				}
			}
		}
		return change;
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
	 * @param fileSpec
	 * @return
	 * @throws Exception
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

	private void printFile(String rev) throws Exception {
		byte[] buf = new byte[1024 * 64];

		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(rev);
		GetFileContentsOptions printOpts = new GetFileContentsOptions();
		printOpts.setNoHeaderLine(true);
		InputStream ins = connection.getFileContents(file, printOpts);

		String localPath = depotToLocal(file.get(0));
		File target = new File(localPath);
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
	 * @param review
	 * @throws Exception
	 */
	public void unshelveFiles(int review) throws Exception {
		// skip if review is 0 or less
		if (review < 1) {
			log("P4 Task: skipping review: " + review);
			return;
		}

		TimeTask timer = new TimeTask();
		log("P4 Task: unshelve review: " + review);

		// Unshelve change for review
		List<IFileSpec> shelveMsg;
		shelveMsg = iclient.unshelveChangelist(review, null, 0, true, false);
		validate.check(shelveMsg, false, "also opened by", "no such file(s)", "exclusive file already opened");

		// force sync any files missed due to INFO messages e.g. exclusive files
		for (IFileSpec spec : shelveMsg) {
			if (spec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = spec.getStatusMessage();
				if (msg.contains("exclusive file already opened")) {
					String rev = msg.substring(0, msg.indexOf(" - can't "));
					printFile(rev);
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
	 * @param mode
	 * @throws Exception
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
		validate.check(rsvMsg, "no file(s) to resolve");

		log("... duration: " + timer.toString());
	}

	/**
	 * Get the change number for the last change within the scope of the
	 * workspace view.
	 *
	 * @return
	 * @throws Exception
	 */
	public int getClientHead() throws Exception {
		// get last change in server
		// This will returned the also shelved CLs
		String latestChange = connection.getCounter("change");
		int change = Integer.parseInt(latestChange);

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		// Question do we only want the last submmited change ? (not a shelved
		// one?)
		opts.setType(IChangelist.Type.SUBMITTED);
		opts.setMaxMostRecent(1);
		List<IChangelistSummary> list = connection.getChangelists(files, opts);

		if (!list.isEmpty() && list.get(0) != null) {
			change = list.get(0).getId();
		} else {
			log("P4: no revisions under " + ws + " using latest change: " + change);
		}
		return change;
	}

	/**
	 * Show all changes within the scope of the client, between the 'from' and
	 * 'to' change limits.
	 *
	 * @param from
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listChanges(P4Revision from, P4Revision to) throws Exception {
		// return empty array, if from and to are equal, or Perforce will report
		// a change
		if (from.equals(to)) {
			return new ArrayList<Integer>();
		}

		String ws = "//" + iclient.getName() + "/...@" + from + "," + to;
		List<Integer> list = listChanges(ws);
		if (!from.isLabel()) {
			Object obj = from.getChange();
			list.remove(obj);
		}
		return list;
	}

	/**
	 * Show all changes within the scope of the client, from the 'from' change
	 * limits.
	 *
	 * @param from
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listChanges(P4Revision from) throws Exception {
		String ws = "//" + iclient.getName() + "/...@" + from + ",now";
		List<Integer> list = listChanges(ws);
		if (!from.isLabel()) {
			Object obj = from.getChange();
			list.remove(obj);
		}
		return list;
	}

	/**
	 * Show all changes within the scope of the client.
	 *
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listChanges() throws Exception {
		String ws = "//" + iclient.getName() + "/...";
		return listChanges(ws);
	}

	private List<Integer> listChanges(String ws) throws Exception {
		List<Integer> list = new ArrayList<Integer>();

		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(ws);
		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(100);
		List<IChangelistSummary> cngs = connection.getChangelists(spec, opts);
		if (cngs != null) {
			for (IChangelistSummary c : cngs) {
				// don't try to add null or -1 changes
				if (c != null && c.getId() != -1) {
					// don't add change entries already in the list
					if (!(list.contains(c.getId()))) {
						list.add(c.getId());
					}
				}
			}
		}

		return list;
	}

	/**
	 * Fetches a list of changes needed to update the workspace to head.
	 *
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listHaveChanges(P4Revision from) throws Exception {
		if (from.getChange() > 0) {
			log("P4: Polling with range: " + from + ",now");
			return listChanges(from);
		}

		String path = "//" + iclient.getName() + "/...";
		return listHaveChanges(path);
	}

	/**
	 * Fetches a list of changes needed to update the workspace to the specified
	 * limit. The limit could be a Perforce change number or label.
	 *
	 * @param changeLimit
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listHaveChanges(P4Revision from, P4Revision changeLimit) throws Exception {
		if (from.getChange() > 0) {
			log("P4: Polling with range: " + from + "," + changeLimit);
			return listChanges(from, changeLimit);
		}

		String path = "//" + iclient.getName() + "/...";
		String fileSpec = path + "@" + changeLimit;
		return listHaveChanges(fileSpec);
	}

	private List<Integer> listHaveChanges(String fileSpec) throws Exception {
		log("P4: Polling with cstat: " + fileSpec);
		
		List<Integer> haveChanges = new ArrayList<Integer>();
		Map<String, Object>[] map;
		map = connection.execMapCmd("cstat", new String[] { fileSpec }, null);

		for (Map<String, Object> entry : map) {
			String status = (String) entry.get("status");
			if (status != null) {
				if (status.startsWith("have")) {
					String value = (String) entry.get("change");
					int change = Integer.parseInt(value);
					haveChanges.add(change);
				}
			}
		}
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
			if (listener != null) {
				log(msg);
			}
			return false;
		}
		return true;
	}

	public IClient getClient() {
		return iclient;
	}
}
