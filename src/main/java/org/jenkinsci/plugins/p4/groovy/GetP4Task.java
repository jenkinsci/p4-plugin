package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4InvalidCredentialException;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serial;
import java.io.Serializable;

public class GetP4Task extends MasterToSlaveCallable<P4Groovy, InterruptedException> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final P4BaseCredentials credential;
	private final Workspace workspace;
	private final FilePath buildWorkspace;

	private final TaskListener listener;

	protected GetP4Task(Run run, String credential, Workspace workspace, FilePath buildWorkspace, TaskListener listener) throws P4InvalidCredentialException {
		this.workspace = workspace;
		this.listener = listener;
		this.buildWorkspace = buildWorkspace;

		this.credential = ConnectionHelper.findCredential(credential, run);
		if (this.credential == null) {
			throw new P4InvalidCredentialException("credential '" + credential + "' not found.");
		}
	}

	@Override
	public P4Groovy call() {
		P4Groovy p4Groovy = new P4Groovy(credential, listener, workspace, buildWorkspace);
		return p4Groovy;
	}
}
