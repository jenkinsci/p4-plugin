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
		if (j == null) {
			return;
		}

		@SuppressWarnings("unchecked")
		Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
		DescriptorImpl p4scm = (DescriptorImpl) scm;

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
			boolean reopen = false;
			String purge = "";
			SubmitImpl publish = new SubmitImpl(desc, success, delete, reopen, purge);

			ClientHelper p4 = getClientHelper(p4scm);
			p4.versionFile(file, publish);
			p4.disconnect();
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
		depotPath = depotPath.endsWith("/") ? depotPath : depotPath + "/";

		Jenkins j = Jenkins.getInstance();
		if (j == null) {
			return null;
		}
		
		String rootPath = j.getRootDir().getCanonicalPath();

		StringBuffer view = new StringBuffer();
		view.append(depotPath + "...");
		view.append(" ");
		view.append("//" + clientName + "/...");

		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, "", "local", view.toString());

		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("utf8", false, clientName, spec);
		workspace.setExpand(new HashMap<String, String>());
		workspace.setRootPath(rootPath);

		ClientHelper p4 = new ClientHelper(Jenkins.getActiveInstance(), credential, listener, clientName, "utf8");
		p4.setClient(workspace);

		return p4;
	}

}
