package org.jenkinsci.plugins.p4.build;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutorHelper {

	private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(), "@");
	private static final String UNKNOWN_EXECUTOR = "0";

	private ExecutorHelper() {
	}

	public static String getExecutorID(FilePath build, TaskListener listener) {
		String id = getExecutorID(build);
		listener.getLogger().println("Executor number at runtime: " + id);
		return id;
	}

	public static String getExecutorID(FilePath build) {
		String id = UNKNOWN_EXECUTOR;
		String name = build.getRemote();
		Pattern pattern = Pattern.compile(".*" + COMBINATOR + "([0-9]+)" + ".*");
		Matcher matcher = pattern.matcher(name);
		if (matcher.matches()) {
			id = matcher.group(1);
		}
		return id;
	}

}