package org.jenkinsci.plugins.p4;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ConfigurationListener extends SaveableListener {

	private static final Logger logger = Logger.getLogger(ConfigurationListener.class.getName());

	public void onChange(Saveable o, XmlFile xml) {
		if (!(o instanceof DescriptorImpl p4scm)) {
			// must be a PerforceScm
			return;
		}

		// Exit early if disabled
		if (!p4scm.isAutoSave()) {
			return;
		}

		try {
			String file = xml.getFile().getCanonicalPath();
			logger.info(">>> onUpdated: " + file);

			// create Publish object
			String desc = "Configuration change";
			boolean success = false;
			boolean delete = true;
			boolean modtime = false;
			boolean reopen = false;
			String purge = "";
			SubmitImpl publish = new SubmitImpl(desc, success, delete, modtime, reopen, purge);

			try (ClientHelper p4 = getClientHelper(p4scm)) {
				int ChangelistID = -1;

				if (!p4scm.isAutoSubmitOnChange()) {
					ChangelistID = p4.findPendingChangelistIDByDesc(desc, p4scm.getClientName());
				}
				p4.versionFile(file, publish, ChangelistID, p4scm.isAutoSubmitOnChange());
			}
		} catch (Exception e) {
			logger.severe(e.getLocalizedMessage());
		}
	}

	private ClientHelper getClientHelper(DescriptorImpl p4scm) throws IOException {

		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);

		String credential = p4scm.getCredential();
		String clientName = p4scm.getClientName();
		String depotPath = p4scm.getDepotPath();

		// check path ends with '/...'
		if (!depotPath.endsWith("/...")) {
			depotPath = depotPath.endsWith("/") ? depotPath + "..." : depotPath + "/...";
		}

		// quote path if it has spaces
		if (depotPath.contains(" ")) {
			depotPath = "\"" + depotPath + "\"";
		}

		Jenkins j = Jenkins.get();
		String rootPath = j.getRootDir().getCanonicalPath();

		String view = ViewMapHelper.getClientView(depotPath, clientName, true);

		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, "", "LOCAL", view, null, null, null, true);

		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("utf8", false, clientName, spec, false);
		workspace.setExpand(new HashMap<>());
		workspace.setRootPath(rootPath);

		return new ClientHelper(Jenkins.get(), credential, listener, workspace);
	}
}
