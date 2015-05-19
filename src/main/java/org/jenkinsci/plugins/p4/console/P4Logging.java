package org.jenkinsci.plugins.p4.console;

import hudson.model.TaskListener;

import com.perforce.p4java.server.callback.ICommandCallback;

public class P4Logging implements ICommandCallback {

	private final TaskListener listener;
	private static int MAX_LINE = 80;

	public P4Logging(TaskListener listener) {
		this.listener = listener;
	}

	public void issuingServerCommand(int key, String commandString) {
		if (commandString.length() > MAX_LINE) {
			String cmd = commandString.substring(0, MAX_LINE);
			cmd = cmd + "___";
			log("(p4):cmd:" + "... p4 " + cmd);
		} else {
			log("(p4):cmd:" + "... p4 " + commandString);
		}
		log("p4 " + commandString + "\n");
	}

	public void completedServerCommand(int key, long millisecsTaken) {
	}

	public void receivedServerInfoLine(int key, String infoLine) {
	}

	public void receivedServerErrorLine(int key, String errorLine) {
	}

	public void receivedServerMessage(int key, int genericCode,
			int severityCode, String message) {
	}

	public TaskListener getListener() {
		return listener;
	}

	private void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}

}
