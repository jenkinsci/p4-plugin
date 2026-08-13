package org.jenkinsci.plugins.p4.tasks;

import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.logging.Logger;

public class DeleteShelfTask extends AbstractTask implements FileCallable<Boolean> {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(DeleteShelfTask.class.getName());

	private long shelf;

	public DeleteShelfTask(String credential, Run<?, ?> run, TaskListener listener) {
		super(credential, run, listener);
	}

	public void setShelf(long shelf) {
		this.shelf = shelf;
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			// Check connection (might be on remote agent)
			if (!checkConnection(p4)) {
				return false;
			}

			p4.deleteShelve(shelf);

		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String err = "Unable to delete shelved change: " + e;
			p4.log(err);
			logger.warning(err);
			throw new AbortException(err);
		}
		return true;
	}

	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

}
