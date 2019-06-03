package org.jenkinsci.plugins.p4;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.scm.SCM;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ConfigurationListener extends SaveableListener {

	private static Logger logger = Logger.getLogger(ConfigurationListener.class.getName());

	public void onChange(Saveable o, XmlFile xml) {
		Jenkins j = Jenkins.getInstance();

		@SuppressWarnings("unchecked")
		Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
		DescriptorImpl p4scm = (DescriptorImpl) scm;

		// Exit early if disabled
		if (p4scm == null || !p4scm.isAutoSave()) {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private ClientHelper getClientHelper(DescriptorImpl p4scm) throws Exception {

		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);

		String credential = p4scm.getCredential();
		String clientName = p4scm.getClientName();
		String depotPath = p4scm.getDepotPath();

		// check path ends with '/...'
		if(!depotPath.endsWith("/...")) {
			depotPath = depotPath.endsWith("/") ? depotPath + "..." : depotPath + "/...";
		}

		// quote path it it has spaces
		if(depotPath.contains(" ")) {
			depotPath = "\"" + depotPath + "\"";
		}

		Jenkins j = Jenkins.getInstance();
		String rootPath = j.getRootDir().getCanonicalPath();

		String view = ViewMapHelper.getClientView(depotPath, clientName, true);

		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, "", "LOCAL", view, null, null, null, true);

		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("utf8", false, clientName, spec);
		workspace.setExpand(new HashMap<String, String>());
		workspace.setRootPath(rootPath);

		ClientHelper p4 = new ClientHelper(Jenkins.getActiveInstance(), credential, listener, workspace);
		return p4;
	}
}
