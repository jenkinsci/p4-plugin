package org.jenkinsci.plugins.p4.client;

import hudson.model.Item;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TempClientHelper extends ClientHelper implements Closeable {

	private static final Logger LOGGER = Logger.getLogger(TempClientHelper.class.getName());

	private final String clientUUID;

	public TempClientHelper(Item context, String credential, TaskListener listener, Workspace workspace) throws Exception {
		super(context, credential, listener);
		String oldName = workspace.getName();

		this.clientUUID = "jenkinsTemp-" + UUID.randomUUID().toString();
		workspace.setName(clientUUID);
		//workspace.setExpand(new HashMap<String, String>());

		// Update view with new name
		if(workspace instanceof ManualWorkspaceImpl) {
			ManualWorkspaceImpl manual = (ManualWorkspaceImpl) workspace;
			WorkspaceSpec spec = manual.getSpec();
			String view = spec.getView();
			view = view.replace(oldName, clientUUID);
			spec.setView(view);
			manual.setSpec(spec);
		}

		clientLogin(workspace);
	}

	@Override
	public void close() throws IOException {
		try {
			deleteClient(clientUUID);
		} catch (Exception e) {
			LOGGER.log(Level.INFO, "Unable to remove temporary client: " + clientUUID);
		}
		disconnect();
	}

	public String getClientUUID() {
		return clientUUID;
	}
}
