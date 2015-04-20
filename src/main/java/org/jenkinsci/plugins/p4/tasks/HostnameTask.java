package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;

public class HostnameTask implements FileCallable<String>, Serializable {

	private static final long serialVersionUID = 1L;

	public String invoke(File f, VirtualChannel channel) throws IOException,
			InterruptedException {
		String hostname = InetAddress.getLocalHost().getHostName();
		return hostname;
	}

}
