package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;

public class HostnameTask implements FileCallable<String>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public String invoke(File f, VirtualChannel channel) throws IOException {
		String hostname = InetAddress.getLocalHost().getHostName();
		return hostname;
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

}
