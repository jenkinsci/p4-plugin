package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Logger;

public class RemoveClientTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(RemoveClientTask.class.getName());

	private boolean deleteClient;
	private boolean deleteFiles;

	public RemoveClientTask(String credential, Run<?, ?> run, TaskListener listener) {
		super(credential, run, listener);
		useGlobalSettings();
	}

	public RemoveClientTask(String credential, Item project, TaskListener listener) {
		super(credential, project, listener);
		useGlobalSettings();
	}

	public void setDeleteClient(boolean deleteClient) {
		this.deleteClient = deleteClient;
	}

	public void setDeleteFiles(boolean deleteFiles) {
		this.deleteFiles = deleteFiles;
	}

	private void useGlobalSettings() {
		Jenkins j = Jenkins.get();
		@SuppressWarnings("unchecked")
		Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
		DescriptorImpl p4scm = (DescriptorImpl) scm;

		if (p4scm != null) {
			deleteClient = p4scm.isDeleteClient();
			deleteFiles = p4scm.isDeleteFiles();
		}
	}

	@Override
	public Object task(ClientHelper p4) {
		logger.info("Task: remove client.");

		String client = getClientName();
		try {
			p4.log("P4 Task: cleanup client: " + client);

			// remove files if required
			if (deleteFiles) {
				ForceCleanImpl forceClean = new ForceCleanImpl(true, true, null, null);
				p4.log("P4 Task: unsyncing client: " + client);
				logger.info("P4: unsyncing client: " + client);
				p4.syncFiles(new P4ChangeRef(0), forceClean);

				// TODO: Jenkins should do this, uncomment if needed.
				// File root = workspace.getCanonicalFile();
				// FileUtils.forceDelete(root);
			}

			// remove client if required
			if (deleteClient) {
				if (p4.isClient(client)) {
					// revert any pending files, before deleting client
					p4.revertAllFiles(false);
					p4.log("P4 Task: remove client: " + client);
					logger.info("P4: remove client: " + client);
					p4.deleteClient(client);
				} else {
					logger.warning("P4: Cannot find: " + client);
					return deleteFiles;
				}
			}
		} catch (Exception e) {
			logger.warning("P4: Not able to get connection");
			return false;
		}
		return deleteFiles;
	}

	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}
}
