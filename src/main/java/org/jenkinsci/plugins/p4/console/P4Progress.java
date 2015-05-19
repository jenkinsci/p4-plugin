package org.jenkinsci.plugins.p4.console;

import hudson.model.TaskListener;

import com.perforce.p4java.server.callback.IProgressCallback;

public class P4Progress implements IProgressCallback {

	private final TaskListener listener;
	private boolean abort = false;

	public P4Progress(TaskListener listener) {
		this.listener = listener;
	}

	public void start(int key) {
	}

	public boolean tick(int key, String msg) {
		if (msg != null && !msg.isEmpty()) {
			StringBuffer sb = new StringBuffer();
			for (String line : msg.split("(?<=\\n)")) {
				sb.append("... " + line);
			}
			log(msg);
		}
		return !abort;
	}

	public void stop(int key) {
		log("(p4):stop:" + key);
	}

	public void abort() {
		this.abort = true;
	}

	private void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}
}
