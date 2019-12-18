package org.jenkinsci.plugins.p4.build;

import hudson.FilePath;
import hudson.slaves.WorkspaceList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutorHelper {

	private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(), "@");
	public static final String UNKNOWN_EXECUTOR = "0";

	private ExecutorHelper() {
	}

	public static String getExecutorID(FilePath build) {
		String id = UNKNOWN_EXECUTOR;
		String workspaceDir = build.getRemote();
		if (!workspaceDir.contains(COMBINATOR)) {
			return id;
		}
		for (String directory : workspaceDir.replace("\\", "/").split("/")) {
			if (directory.contains(COMBINATOR)) {
				Pattern pattern = Pattern.compile(".*" + COMBINATOR + "([0-9]+)");
				Matcher matcher = pattern.matcher(directory);
				if (matcher.matches()) {
					id = matcher.group(1);
				}
			}
		}
		return id;
	}

}
