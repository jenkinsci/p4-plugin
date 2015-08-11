package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;

import jenkins.security.Roles;

import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

public class HostnameTask implements FileCallable<String>, Serializable {

	private static final long serialVersionUID = 1L;

	public String invoke(File f, VirtualChannel channel) throws IOException,
			InterruptedException {
		String hostname = InetAddress.getLocalHost().getHostName();
		return hostname;
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

}
