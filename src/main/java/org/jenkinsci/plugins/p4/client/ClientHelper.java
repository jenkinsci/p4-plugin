package org.jenkinsci.plugins.p4.client;

import hudson.AbortException;
import hudson.model.TaskListener;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.tasks.TimeTask;
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
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.option.changelist.SubmitOptions;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.option.server.OpenedFilesOptions;

public class ClientHelper extends ConnectionHelper {

	private static Logger logger = Logger.getLogger(ClientHelper.class
			.getName());

	private IClient iclient;

	public ClientHelper(String credential, TaskListener listener, String client) {
		super(credential, listener);
		clientLogin(client);
	}

	public ClientHelper(P4StandardCredentials credential,
			TaskListener listener, String client) {
		super(credential, listener);
		clientLogin(client);
	}

	private void clientLogin(String client) {
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
	}

	public boolean setClient(Workspace workspace) throws Exception {

		if (isUnicode()) {
			String charset = "utf8";
			connection.setCharsetName(charset);
		}

		// Setup/Create workspace based on type
		iclient = workspace.setClient(connection,
				authorisationConfig.getUsername());

		// Exit early if client is not defined
		if (!isClientValid(workspace)) {
			return false;
		}

		// Ensure root and host fields are set
		iclient.setRoot(workspace.getRootPath());
		iclient.setHostName(workspace.getHostName());

		// Set clobber on to ensure workspace is always good
		IClientOptions options = iclient.getOptions();
		options.setClobber(true);
		iclient.setOptions(options);

		// Save client spec
		iclient.update();

		// Set active client for this connection
		connection.setCurrentClient(iclient);
		return true;
	}

	/**
	 * Test to see if workspace is at the latest revision.
	 * 
	 * @throws Exception
	 */
	public boolean updateFiles() throws Exception {
		// build file revision spec
		List<IFileSpec> syncFiles;
		String path = iclient.getRoot() + "/...";
		syncFiles = FileSpecBuilder.makeFileSpecList(path);

		// Sync revision to re-edit
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setNoUpdate(true);
		List<IFileSpec> syncMsg = iclient.sync(syncFiles, syncOpts);

		for (IFileSpec fileSpec : syncMsg) {
			if (fileSpec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = fileSpec.getStatusMessage();
				if (msg.contains("file(s) up-to-date.")) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Sync files to workspace at the specified change/label.
	 * 
	 * @param change
	 * @throws Exception
	 */
	public void syncFiles(Object buildChange, Populate populate)
			throws Exception {
		TimeTask timer = new TimeTask();

		// test label is valid
		if (buildChange instanceof String) {
			String label = (String) buildChange;
			try {
				int change = Integer.parseInt(label);
				log("SCM Task: label is a number! syncing files at change: "
						+ change);
			} catch (NumberFormatException e) {
				if (!isLabel(label) && !isClient(label)) {
					String msg = "P4: Unable to find client/label: " + label;
					log(msg);
					logger.warning(msg);
					throw new AbortException(msg);
				} else {
					log("SCM Task: syncing files at client/label: " + label);
				}
			}
		} else {
			log("SCM Task: syncing files at change: " + buildChange);
		}

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		String revisions = path + "@" + buildChange;
		log("... sync " + revisions);

		// Sync files
		files = FileSpecBuilder.makeFileSpecList(revisions);
		syncFiles(files, populate);
		log("... duration: " + timer.toString());
	}

	private void syncFiles(List<IFileSpec> files, Populate populate)
			throws Exception {

		// set MODTIME if populate options is used
		if (populate.isModtime()) {
			IClientOptions options = iclient.getOptions();
			if (!options.isModtime()) {
				options.setModtime(true);
				iclient.setOptions(options);
				iclient.update(); // Save client spec
				// iclient.refresh();
				// connection.setCurrentClient(iclient);
			}
		}

		// sync options
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setServerBypass(!populate.isHave() && !populate.isForce());
		syncOpts.setForceUpdate(populate.isForce());
		log("... force update " + populate.isForce());
		log("... bypass have " + !populate.isHave());

		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		validateFileSpecs(syncMsg, "file(s) up-to-date.",
				"file does not exist", "no file(s) as of that date");
	}

	/**
	 * Cleans up the Perforce workspace after a previous build. Removes all
	 * pending and abandoned files (equivalent to 'p4 revert -w').
	 * 
	 * @throws Exception
	 */
	public void tidyWorkspace(Populate populate) throws Exception {

		log("SCM Task: cleanup workspace: " + iclient.getName());

		// relies on workspace view for scope.
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		files = FileSpecBuilder.makeFileSpecList(path);

		if (populate instanceof AutoCleanImpl) {
			// remove all pending files within workspace
			tidyPending(files);

			// remove extra files within workspace
			if (((AutoCleanImpl) populate).isDelete()) {
				tidyDelete(files);
			}

			// replace any missing/modified files within workspace
			if (((AutoCleanImpl) populate).isReplace()) {
				tidyReplace(files, populate);
			}
		}

		if (populate instanceof ForceCleanImpl) {

			// remove all pending files within workspace
			tidyPending(files);

			// remove all versioned files (clean have list)
			syncFiles(0, populate);

			// remove all files from workspace
			String root = iclient.getRoot();
			log("... rm -rf " + root);
			FileUtils.forceDelete(new File(root));
		}
	}

	private void tidyPending(List<IFileSpec> files) throws Exception {
		TimeTask timer = new TimeTask();
		log("SCM Task: reverting all pending and shelved revisions.");

		// revert all pending and shelved revisions
		RevertFilesOptions rOpts = new RevertFilesOptions();
		log("... [list] = revert");

		List<IFileSpec> list = iclient.revertFiles(files, rOpts);
		validateFileSpecs(list, "not opened on this client");
		log("... size[list] = " + list.size());

		// check for added files and remove...
		log("... rm [list] | ABANDONED");
		for (IFileSpec file : list) {
			if (file.getAction() == FileAction.ABANDONED) {
				// first check if we have the local path
				String path = file.getLocalPathString();
				if (path == null) {
					path = depotToLocal(file);
				}
				if (path != null) {
					File unlink = new File(path);
					unlink.delete();
				}
			}
		}
		log("... duration: " + timer.toString());
	}

	private void tidyReplace(List<IFileSpec> files, Populate populate)
			throws Exception {
		TimeTask timer = new TimeTask();
		log("SCM Task: restoring all missing and changed revisions.");

		// check status - find all missing or changed
		String[] base = { "-n", "-e", "-d", "-f" };
		List<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(base));

		// set MODTIME if populate options is used and server supports flag
		if (populate.isModtime()) {
			if (checkVersion(20141)) {
				list.add("-m");
			} else {
				log("P4: Resolving files by MODTIME not supported (requires 2014.1)");
			}
		}

		String[] args = list.toArray(new String[list.size()]);
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions(args);

		log("... [list] = reconcile " + list.toString());
		List<IFileSpec> update = iclient.reconcileFiles(files, statusOpts);
		validateFileSpecs(update, "also opened by", "no file(s) to reconcile",
				"must sync/resolve", "exclusive file already opened",
				"cannot submit from stream");

		log("... size[list] = " + update.size());

		// force sync to update files only if "no file(s) to reconcile" is not
		// present.
		if (validateFileSpecs(update, true, "also opened by",
				"must sync/resolve", "exclusive file already opened",
				"cannot submit from stream")) {
			SyncOptions syncOpts = new SyncOptions();
			syncOpts.setForceUpdate(true);
			log("... sync -f [list]");
			List<IFileSpec> syncMsg = iclient.sync(update, syncOpts);
			validateFileSpecs(syncMsg, "file(s) up-to-date.",
					"file does not exist");
		}

		// force sync any files missed due to INFO messages e.g. exclusive files
		for (IFileSpec spec : update) {
			if (spec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = spec.getStatusMessage();
				if (msg.contains("exclusive file already opened")) {
					String rev = msg.substring(0, msg.indexOf(" - can't "));
					List<IFileSpec> f = FileSpecBuilder.makeFileSpecList(rev);

					SyncOptions syncOpts = new SyncOptions();
					syncOpts.setForceUpdate(true);
					log("... sync -f " + rev);
					List<IFileSpec> syncMsg = iclient.sync(f, syncOpts);
					validateFileSpecs(syncMsg, "file(s) up-to-date.",
							"file does not exist");
				}
			}
		}
		log("... duration: " + timer.toString());
	}

	private void tidyDelete(List<IFileSpec> files) throws Exception {
		TimeTask timer = new TimeTask();
		log("SCM Task: removing all non-versioned files.");

		// check status - find all extra files
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts.setOutsideAdd(true);
		statusOpts.setNoUpdate(true);
		statusOpts.setUseWildcards(true);
		log("... [list] = reconcile -n -a");
		List<IFileSpec> extra = iclient.reconcileFiles(files, statusOpts);
		validateFileSpecs(extra, "- no file(s) to reconcile", "instead of",
				"empty, assuming text");

		log("... size[list] = " + extra.size());

		// remove added files
		log("... rm [list]");
		for (IFileSpec e : extra) {
			String path = e.getLocalPathString();
			if (path == null) {
				path = depotToLocal(e);
			}
			if (path != null) {
				File unlink = new File(path);
				unlink.delete();
			}
		}
		log("... duration: " + timer.toString());
	}

	public boolean buildChange() throws Exception {
		TimeTask timer = new TimeTask();
		log("SCM Task: reconcile files to changelist.");

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		// cleanup pending changes (revert -k)
		RevertFilesOptions revertOpts = new RevertFilesOptions();
		revertOpts.setNoClientRefresh(true);
		log("... revert -k");
		List<IFileSpec> revertStat = iclient.revertFiles(files, revertOpts);
		validateFileSpecs(revertStat, "");

		// flush client to populate have (sync -k)
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setClientBypass(true);
		log("... sync -k");
		List<IFileSpec> syncStat = iclient.sync(files, syncOpts);
		validateFileSpecs(syncStat, "file(s) up-to-date.");

		// check status - find all changes to files
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts.setUseWildcards(true);
		log("... [list] = reconcile");
		List<IFileSpec> status = iclient.reconcileFiles(files, statusOpts);
		validateFileSpecs(status, "- no file(s) to reconcile", "instead of",
				"empty, assuming text", "also opened by");

		// Check if file is open
		boolean open = isOpened(files);

		log("... duration: " + timer.toString());
		return open;
	}

	public void publishChange(Publish publish) throws Exception {
		TimeTask timer = new TimeTask();
		log("SCM Task: publish files to Perforce.");

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		// create new pending change and add description
		IChangelist change = new Changelist();
		change.setDescription(publish.getExpandedDesc());
		change = iclient.createChangelist(change);
		log("... pending change: " + change.getId());

		// move files from default change
		ReopenFilesOptions reopenOpts = new ReopenFilesOptions();
		reopenOpts.setChangelistId(change.getId());
		iclient.reopenFiles(files, reopenOpts);

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
			SubmitOptions submitOpts = new SubmitOptions();
			submitOpts.setReOpen(submit.isReopen());
			change.submit(submitOpts);
			log("... submitting files");
		}

		// if SHELVE
		if (publish instanceof ShelveImpl) {
			ShelveImpl shelve = (ShelveImpl) publish;
			log("... shelving files");
			iclient.shelveChangelist(change);

			// post shelf cleanup
			RevertFilesOptions revertOpts = new RevertFilesOptions();
			revertOpts.setChangelistId(change.getId());
			revertOpts.setNoClientRefresh(!shelve.isRevert());
			String r = (shelve.isRevert()) ? "(revert)" : "(revert -k)";
			log("... reverting open files " + r);
			iclient.revertFiles(files, revertOpts);
		}
		log("... duration: " + timer.toString());
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
		TimeTask timer = new TimeTask();
		log("SCM Task: unshelve review: " + review);

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		files = FileSpecBuilder.makeFileSpecList(path);

		// Unshelve change for review
		List<IFileSpec> shelveMsg;
		log("... unshelve -f -s " + review);
		shelveMsg = iclient.unshelveChangelist(review, null, 0, true, false);
		validateFileSpecs(shelveMsg, "also opened by", "no such file(s)",
				"exclusive file already opened");

		// force sync any files missed due to INFO messages e.g. exclusive files
		for (IFileSpec spec : shelveMsg) {
			if (spec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = spec.getStatusMessage();
				if (msg.contains("exclusive file already opened")) {
					String rev = msg.substring(0, msg.indexOf(" - can't "));
					printFile(rev);
					log("... print " + rev);
				}
			}
		}

		// Remove opened files from have list.
		RevertFilesOptions rOpts = new RevertFilesOptions();
		rOpts.setNoUpdate(true);
		log("... revert -k " + path);
		List<IFileSpec> rvtMsg = iclient.revertFiles(files, rOpts);
		validateFileSpecs(rvtMsg, "file(s) not opened on this client");
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
		String latestChange = connection.getCounter("change");
		int change = Integer.parseInt(latestChange);

		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(1);
		List<IChangelistSummary> list = connection.getChangelists(files, opts);

		if (!list.isEmpty() && list.get(0) != null) {
			change = list.get(0).getId();
		} else {
			log("P4: no revisions under " + ws + " using latest change: "
					+ change);
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
	public List<Integer> listChanges(Object from, Object to) throws Exception {
		// return empty array, if from and to are equal, or Perforce will report
		// a change
		if (from.equals(to)) {
			return new ArrayList<Integer>();
		}

		String ws = "//" + iclient.getName() + "/...@" + from + "," + to;
		List<Integer> list = listChanges(ws);
		list.remove(from);
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
	public List<Integer> listChanges(Object from) throws Exception {
		String ws = "//" + iclient.getName() + "/...@" + from + ",now";
		List<Integer> list = listChanges(ws);
		list.remove(from);
		return list;
	}

	/**
	 * Show all changes within the scope of the client.
	 * 
	 * @param from
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listChanges() throws Exception {
		String ws = "//" + iclient.getName() + "/...";
		return listChanges(ws);
	}

	private List<Integer> listChanges(String ws) throws Exception {
		List<Integer> list = new ArrayList<Integer>();

		String msg = "listing changes: " + ws;
		log(msg);
		logger.info(msg);

		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(ws);
		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(100);
		List<IChangelistSummary> cngs = connection.getChangelists(spec, opts);
		if (cngs != null) {
			for (IChangelistSummary c : cngs) {
				list.add(c.getId());
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
	public List<Integer> listHaveChanges() throws Exception {
		String path = "//" + iclient.getName() + "/...";
		return listHaveChanges(path);
	}

	/**
	 * Fetches a list of changes needed to update the workspace to the specified
	 * limit. The limit could be a Perforce change number or label.
	 * 
	 * @param limit
	 * @return
	 * @throws Exception
	 */
	public List<Integer> listHaveChanges(Object changeLimit) throws Exception {
		String path = "//" + iclient.getName() + "/...";
		String fileSpec = path + "@" + changeLimit;
		return listHaveChanges(fileSpec);
	}

	private List<Integer> listHaveChanges(String fileSpec) throws Exception {
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
				msg = "P4: Template worksapce not found: " + name;
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
