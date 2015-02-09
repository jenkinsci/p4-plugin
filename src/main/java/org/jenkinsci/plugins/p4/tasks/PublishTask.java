package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import jenkins.security.Roles;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

public class PublishTask extends AbstractTask implements FileCallable<Boolean>,
		RoleSensitive, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger
			.getLogger(PublishTask.class.getName());

	private final Publish publish;

	public PublishTask(Publish publish) {
		this.publish = publish;
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

	public Boolean invoke(File f, VirtualChannel channel) throws IOException,
			InterruptedException {

		ClientHelper p4 = getConnection();
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}

			// Look for changes and add to change-list, then publish
			boolean open = p4.buildChange();
			if (open) {
				p4.publishChange(publish);
			}
		} catch (Exception e) {
			String msg = "Unable to publish workspace: " + e;
			logger.warning(msg);
			throw new InterruptedException(msg);
		} finally {
			p4.disconnect();
		}
		return true;
	}
}
