package org.jenkinsci.plugins.p4.groovy;

import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serializable;

public class GetP4Task extends MasterToSlaveCallable<P4Groovy, InterruptedException> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String credential;
	private final Workspace workspace;

	private final TaskListener listener;

	public GetP4Task(String credential, Workspace workspace, TaskListener listener) {
		this.credential = credential;
		this.workspace = workspace;
		this.listener = listener;
	}

	@Override
	public P4Groovy call() throws InterruptedException {
		P4Groovy p4Groovy = new P4Groovy(credential, listener, workspace);
		return p4Groovy;
	}
}
