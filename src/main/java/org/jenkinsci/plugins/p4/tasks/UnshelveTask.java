package org.jenkinsci.plugins.p4.tasks;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;

public class UnshelveTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(UnshelveTask.class.getName());

	private final String resolve;
	private int shelf;

	public UnshelveTask(String resolve) {
		this.resolve = resolve;
	}

	public void setShelf(int shelf) {
		this.shelf = shelf;
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}

			p4.unshelveFiles(shelf);

			p4.resolveFiles(resolve);

		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String msg = "Unable to publish workspace: " + e;
			logger.warning(msg);
			throw e;
		} finally {
			p4.disconnect();
		}
		return true;
	}

	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

}
