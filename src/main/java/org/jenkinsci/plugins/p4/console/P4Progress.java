package org.jenkinsci.plugins.p4.console;

import com.perforce.p4java.server.callback.IProgressCallback;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.SessionHelper;

public class P4Progress implements IProgressCallback {

	private final TaskListener listener;
	private final SessionHelper p4;
	private final boolean hideMessages;

	public P4Progress(TaskListener listener, SessionHelper p4) {
		this.listener = listener;
		this.p4 = p4;
		if (p4.getP4SCM() != null) {
			this.hideMessages = p4.getP4SCM().isHideMessages();
		} else {
			this.hideMessages = false;
		}
	}

	public void start(int key) {
	}

	public boolean tick(int key, String msg) {
		if (!hideMessages && msg != null && !msg.isEmpty()) {
			log(msg);
		}

		if (Thread.interrupted()) {
			log("(p4):stop:exception\n");
			log("P4: ABORT called!");
			p4.abort();
			return false;
		}

		return true;
	}

	public void stop(int key) {
		log("(p4):stop:" + key);
	}

	private void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}
}
