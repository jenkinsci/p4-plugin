package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WhereTask extends AbstractTask implements FilePath.FileCallable<String>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String script;

	public WhereTask(String credential, Run run, TaskListener listener, String script) {
		super(credential, run, listener);
		this.script = script;
	}

	/**
	 * Invoke sync on build node (master or remote node).
	 *
	 * @return true if updated, false if no change.
	 */
	public String invoke(File workspace, VirtualChannel channel) throws IOException {
		return (String) tryTask();
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		String scriptPath = null;
		if (script != null && !script.isEmpty()) {
			String root = p4.getClient().getRoot();
			Path path = Paths.get(root, script);
			scriptPath = p4.where(path.toString());
		}
		return scriptPath;
	}
}