package org.jenkinsci.plugins.p4.client;

import hudson.AbortException;
import hudson.model.Item;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;

import java.io.Closeable;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TempClientHelper extends ClientHelper implements Closeable {

	private static final Logger LOGGER = Logger.getLogger(TempClientHelper.class.getName());

	private final String clientUUID;

	public TempClientHelper(Item context, String credential, TaskListener listener, Workspace workspace) throws Exception {
		super(context, credential, listener);
		this.clientUUID = "jenkinsTemp-" + UUID.randomUUID();
		if (workspace != null) {
			update(workspace);
		}
	}

	@Override
	public void close() {
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

	public void update(Workspace workspace) throws AbortException {
		String oldName = workspace.getName();
		workspace.setName(clientUUID);

		// Update view with new name
		if (workspace instanceof ManualWorkspaceImpl manual) {
			WorkspaceSpec spec = manual.getSpec();
			String view = spec.getView();
			view = view.replace(oldName, clientUUID);
			spec.setView(view);
			manual.setSpec(spec);
		}

		clientLogin(workspace);
	}
}
