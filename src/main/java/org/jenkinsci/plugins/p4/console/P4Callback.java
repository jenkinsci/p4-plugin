package org.jenkinsci.plugins.p4.console;

import hudson.model.TaskListener;

import com.perforce.p4java.server.callback.IProgressCallback;

public class P4Callback implements IProgressCallback {

	private final TaskListener listener;
	private boolean abort = false;
	private boolean started = false;

	public P4Callback(TaskListener listener) {
		this.listener = listener;
	}

	public void start(int key) {
	}

	public boolean tick(int key, String msg) {

		if (msg != null && !msg.isEmpty()) {
			if (!started) {
				started = true;
				log("[p4log:start:" + key + "]");
			}

			StringBuffer sb = new StringBuffer();
			for (String line : msg.split("(?<=\\n)")) {
				sb.append("... " + line);
			}
			log(sb.toString());
		}
		return !abort;
	}

	public void stop(int key) {
		if (started) {
			log("[p4log:stop:" + key + "]");
			started = false;
		}
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
