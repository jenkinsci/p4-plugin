package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class GetP4Task implements FileCallable<P4Groovy>, Serializable {

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
	public P4Groovy invoke(File buildWorkspace, VirtualChannel channel) throws IOException, InterruptedException {
		P4Groovy p4Groovy = new P4Groovy(credential, listener, workspace);
		return p4Groovy;
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}
}
