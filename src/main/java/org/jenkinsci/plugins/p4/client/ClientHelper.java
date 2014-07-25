package org.jenkinsci.plugins.p4.client;

import hudson.model.TaskListener;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.client.ClientOptions;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.server.CmdSpec;

public class ClientHelper extends ConnectionHelper {

	private static Logger logger = Logger.getLogger(ClientHelper.class
			.getName());

	private IClient iclient;

	public ClientHelper(String credential, TaskListener listener, String client) {
		super(credential, listener);
		clientLogin(listener, client);
	}

	public ClientHelper(P4StandardCredentials credential,
			TaskListener listener, String client) {
		super(credential, listener);
		clientLogin(listener, client);
	}

	private void clientLogin(TaskListener listener, String client) {
		// Login to Perforce
		try {
			login();
		} catch (Exception e) {
			String err = "P4: Unable to login: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
		}

		// Find workspace and set as current
		try {
			iclient = connection.getClient(client);
			connection.setCurrentClient(iclient);
		} catch (Exception e) {
			String err = "P4: Unable to use Workspace: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
		}
	}

	public boolean setClient(Workspace workspace) throws Exception {

		if (connectionConfig.isUnicode()) {
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
		ClientOptions options = new ClientOptions();
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
	 * Sync files to workspace at the specified change.
	 * 
	 * @param change
	 * @throws Exception
	 */
	public boolean syncFiles(int change, Populate populate) throws Exception {
		log("SCM Task: syncing files at change: " + change);

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		String revisions = path + "@" + change;
		log("... sync " + revisions);

		// Sync files
		files = FileSpecBuilder.makeFileSpecList(revisions);
		return syncFiles(files, populate);
	}

	/**
	 * Sync files to workspace at the specified label.
	 * 
	 * @param change
	 * @throws Exception
	 */
	public boolean syncFiles(String label, Populate populate) throws Exception {
		log("SCM Task: syncing files at label: " + label);

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		String revisions = path + "@" + label;
		log("... sync " + revisions);

		// Sync files
		files = FileSpecBuilder.makeFileSpecList(revisions);
		return syncFiles(files, populate);
	}

	private boolean syncFiles(List<IFileSpec> files, Populate populate)
			throws Exception {

		boolean success = true;

		// sync options
		SyncOptions syncOpts = new SyncOptions();
		syncOpts.setServerBypass(!populate.isHave());	
		syncOpts.setForceUpdate(populate.isForce() && populate.isHave());		
		log("... force update " + populate.isForce());
		log("... bypass have " + !populate.isHave());

		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		success &= validateFileSpecs(syncMsg, "file(s) up-to-date.");
		return success;
	}

	/**
	 * Cleans up the Perforce workspace after a previous build. Removes all
	 * pending and abandoned files (equivalent to 'p4 revert -w').
	 * 
	 * @throws Exception
	 */
	public boolean tidyWorkspace(Populate populate) throws Exception {
		boolean success = true;

		log("SCM Task: cleanup workspace: " + iclient.getName());

		// relies on workspace view for scope.
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		files = FileSpecBuilder.makeFileSpecList(path);

		if (populate instanceof AutoCleanImpl) {
			// remove all pending files within workspace
			success &= tidyPending(files);

			// remove extra files within workspace
			if (((AutoCleanImpl) populate).isDelete()) {
				success &= tidyDelete(files);
			}

			// replace any missing/modified files within workspace
			if (((AutoCleanImpl) populate).isReplace()) {
				success &= tidyReplace(files);
			}
		}

		if (populate instanceof ForceCleanImpl) {
			// remove all files from workspace
			String root = iclient.getRoot();
			File unlink = new File(root);
			unlink.delete();
		}

		return success;
	}

	private boolean tidyPending(List<IFileSpec> files) throws Exception {
		boolean success = true;

		log("SCM Task: reverting all pending and shelved revisions.");

		// revert all pending and shelved revisions
		RevertFilesOptions rOpts = new RevertFilesOptions();
		log("... [list] = revert");
		List<IFileSpec> list = iclient.revertFiles(files, rOpts);
		success &= validateFileSpecs(list, "not opened on this client");

		// check for added files and remove...
		log("... rm [list] | ABANDONED");
		for (IFileSpec file : list) {
			if (file.getAction() == FileAction.ABANDONED) {
				String path = depotToLocal(file);
				if (path != null) {
					File unlink = new File(path);
					unlink.delete();
				}
			}
		}
		return success;
	}

	private boolean tidyReplace(List<IFileSpec> files) throws Exception {
		boolean success = true;

		log("SCM Task: restoring all missing and changed revisions.");

		// check status - find all missing or changed
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts = new ReconcileFilesOptions();
		statusOpts.setNoUpdate(true);
		statusOpts.setOutsideEdit(true);
		statusOpts.setRemoved(true);
		statusOpts.setUseWildcards(true);
		log("... [list] = reconcile -n -ed");
		List<IFileSpec> update = iclient.reconcileFiles(files, statusOpts);
		success &= validateFileSpecs(update, "also opened by",
				"no file(s) to reconcile", "must sync/resolve",
				"exclusive file already opened");

		// force sync to update files only if "no file(s) to reconcile" is not
		// present.
		if (validateFileSpecs(update, true, "also opened by",
				"must sync/resolve", "exclusive file already opened")) {
			SyncOptions syncOpts = new SyncOptions();
			syncOpts.setForceUpdate(true);
			log("... sync -f [list]");
			List<IFileSpec> syncMsg = iclient.sync(update, syncOpts);
			success &= validateFileSpecs(syncMsg, "file(s) up-to-date.",
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
					success &= validateFileSpecs(syncMsg,
							"file(s) up-to-date.", "file does not exist");
				}
			}
		}

		return success;
	}

	private boolean tidyDelete(List<IFileSpec> files) throws Exception {
		boolean success = true;

		log("SCM Task: removing all non-versioned files.");

		// check status - find all extra files
		ReconcileFilesOptions statusOpts = new ReconcileFilesOptions();
		statusOpts.setOutsideAdd(true);
		statusOpts.setNoUpdate(true);
		statusOpts.setUseWildcards(true);
		log("... [list] = reconcile -n -a");
		List<IFileSpec> extra = iclient.reconcileFiles(files, statusOpts);
		success &= validateFileSpecs(extra, "- no file(s) to reconcile",
				"instead of", "empty, assuming text");

		// remove added files
		log("... rm [list]");
		for (IFileSpec e : extra) {
			String path = depotToLocal(e);
			if (path != null) {
				File unlink = new File(path);
				unlink.delete();
			}
		}

		return success;
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
	public boolean unshelveFiles(int review) throws Exception {
		boolean success = true;

		log("SCM Task: unshelve review: " + review);

		// build file revision spec
		List<IFileSpec> files;
		String path = iclient.getRoot() + "/...";
		files = FileSpecBuilder.makeFileSpecList(path);

		// Sync workspace to head
		SyncOptions syncOpts = new SyncOptions();
		log("... sync " + path);
		List<IFileSpec> syncMsg = iclient.sync(files, syncOpts);
		success &= validateFileSpecs(syncMsg, "file(s) up-to-date.");

		// Unshelve change for review
		List<IFileSpec> shelveMsg;
		log("... unshelve -f -s " + review + " " + path);
		shelveMsg = iclient.unshelveChangelist(review, files, 0, true, false);
		success &= validateFileSpecs(shelveMsg, "also opened by",
				"no such file(s)", "exclusive file already opened");

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
		success &= validateFileSpecs(rvtMsg,
				"file(s) not opened on this client");

		return success;
	}

	/**
	 * Get the change number for the last change within the scope of the
	 * workspace view.
	 * 
	 * @return
	 * @throws Exception
	 */
	public int getClientHead() throws Exception {
		// build file revision spec
		String ws = "//" + iclient.getName() + "/...";
		List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(ws);

		GetChangelistsOptions opts = new GetChangelistsOptions();
		opts.setMaxMostRecent(1);
		List<IChangelistSummary> list = connection.getChangelists(files, opts);

		int change = 0;
		if (!list.isEmpty() && list.get(0) != null) {
			change = list.get(0).getId();
		}
		return change;
	}

	/**
	 * Gets the Changelist (p4 describe -s); shouldn't need a client, but
	 * p4-java throws an exception if one is not set.
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public Changelist getChange(int id) throws Exception {
		return (Changelist) connection.getChangelist(id);
	}

	/**
	 * Fetches a list of changes needed to update the workspace to head.
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Object> listChanges() throws Exception {
		String path = iclient.getRoot() + "/...";
		return listChanges(path);
	}

	/**
	 * Fetches a list of changes needed to update the workspace to the specified
	 * limit. The limit could be a Perforce change number or label.
	 * 
	 * @param limit
	 * @return
	 * @throws Exception
	 */
	public List<Object> listChanges(int changeLimit) throws Exception {
		String path = iclient.getRoot() + "/...";
		String fileSpec = path + "@" + changeLimit;
		return listChanges(fileSpec);
	}

	private List<Object> listChanges(String fileSpec) throws Exception {
		List<Object> changes = new ArrayList<Object>();
		Map<String, Object>[] map;
		map = connection.execMapCmd("cstat", new String[] { fileSpec }, null);

		for (Map<String, Object> entry : map) {
			String status = (String) entry.get("status");
			if (status.startsWith("need")) {
				String value = (String) entry.get("change");
				int change = Integer.parseInt(value);
				changes.add(change);
			} else if (status.startsWith("partial")) {
				String value = (String) entry.get("change");
				int change = Integer.parseInt(value);
				changes.add(change);
			}
		}
		return changes;
	}

	public Label getLabel(String id) throws Exception {
		return (Label) connection.getLabel(id);
	}

	public void setLabel(Label label) throws Exception {
		// connection.createLabel(label);
		String user = connection.getUserName();
		label.setOwnerName(user);
		connection.updateLabel(label);
	}

	public List<IFileSpec> getTaggedFiles(String label) throws Exception {
		String ws = "//" + iclient.getName() + "/...@" + label;
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(ws);
		List<IFileSpec> tagged = connection.getDepotFiles(spec, false);
		return tagged;
	}

	public List<IFileSpec> loadShelvedFiles(int id) throws Exception {
		String cmd = CmdSpec.DESCRIBE.name();
		String[] args = new String[] { "-s", "-S", "" + id };
		List<Map<String, Object>> resultMaps;
		resultMaps = connection.execMapCmdList(cmd, args, null);

		List<IFileSpec> list = new ArrayList<IFileSpec>();

		if (resultMaps != null) {
			if ((resultMaps.size() > 0) && (resultMaps.get(0) != null)) {
				Map<String, Object> map = resultMaps.get(0);
				if (map.containsKey("shelved")) {
					for (int i = 0; map.get("rev" + i) != null; i++) {
						FileSpec fSpec = new FileSpec(map, connection, i);
						fSpec.setChangelistId(id);
						list.add(fSpec);
					}
				}
			}
		}
		return list;
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
				listener.getLogger().println(msg);
			}
			return false;
		}
		return true;
	}
}
