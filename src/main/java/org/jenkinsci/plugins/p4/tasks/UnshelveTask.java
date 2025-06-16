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
import java.io.Serializable;
import java.util.logging.Logger;

public class UnshelveTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(UnshelveTask.class.getName());

	private final String resolve;
	private final boolean tidy;
	private long shelf;

	public UnshelveTask(String credential, Run<?, ?> run, TaskListener listener, String resolve, boolean tidy) {
		super(credential, run, listener);
		this.resolve = resolve;
		this.tidy = tidy;
	}

	public void setShelf(long shelf) {
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

			if(tidy) {
				p4.revertAllFiles(true);
			}

		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String err = "Unable to unshelve change: " + e;
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
