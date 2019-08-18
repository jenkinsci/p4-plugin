package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.io.Serializable;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

public class GetP4Task extends MasterToSlaveCallable<P4Groovy, InterruptedException> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String credentialName;
	private final Workspace workspace;
	private final FilePath buildWorkspace;
	private final P4BaseCredentials p4Credential;

	private final TaskListener listener;

	public GetP4Task(String credentialName, Workspace workspace, FilePath buildWorkspace, 
			TaskListener listener, Run run) {
		this.credentialName = credentialName;
		this.workspace = workspace;
		this.listener = listener;
		this.buildWorkspace = buildWorkspace;
		
		// TODO:  throw exception here if not found.
		this.p4Credential = ConnectionHelper.findCredential(credentialName, run);
	}

	@Override
	public P4Groovy call() throws InterruptedException {
		P4Groovy p4Groovy = new P4Groovy(credentialName, p4Credential, listener, workspace, buildWorkspace);
		return p4Groovy;
	}
}
