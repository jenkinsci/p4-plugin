package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

public class RemoveClientTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(RemoveClientTask.class.getName());

	private final boolean deleteClient;
	private final boolean deleteFiles;

	public RemoveClientTask() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			@SuppressWarnings("unchecked")
			Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
			DescriptorImpl p4scm = (DescriptorImpl) scm;

			deleteClient = p4scm.isDeleteClient();
			deleteFiles = p4scm.isDeleteFiles();
		} else {
			logger.warning("Unable to read PerforceScm global descriptor.");
			deleteClient = false;
			deleteFiles = false;
		}
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		logger.info("Task: remove client.");

		String client = getClient();
		try {
			// remove files if required
			if (deleteFiles) {
				ForceCleanImpl forceClean = new ForceCleanImpl(true, true, null, null);
				logger.info("P4: unsyncing client: " + client);
				p4.syncFiles(new P4Revision(0), forceClean);

				// TODO: Jenkins should do this, uncomment if needed.
				// File root = workspace.getCanonicalFile();
				// FileUtils.forceDelete(root);
			}

			// remove client if required
			if (deleteClient) {
				if (p4.isClient(client)) {
					// revert any pending files, before deleting client
					p4.revertAllFiles();
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
		} finally {
			p4.disconnect();
		}
		return deleteFiles;
	}

	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		return (Boolean) tryTask();
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}
}
