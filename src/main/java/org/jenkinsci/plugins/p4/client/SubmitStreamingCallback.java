package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IServer;
import com.perforce.p4java.server.callback.IStreamingCallback;
import hudson.model.TaskListener;

import java.util.Map;

public class SubmitStreamingCallback implements IStreamingCallback {

	private boolean done = false;
	private long change = 0;

	private final Server server;
	private final Validate validate;

	public SubmitStreamingCallback(IServer iserver, TaskListener listener) {
		this.server = (Server) iserver;
		this.validate = new Validate(listener);
	}

	@Override
	public boolean startResults(int key) throws P4JavaException {
		return true;
	}

	@Override
	public boolean endResults(int key) throws P4JavaException {
		done = true;
		return true;
	}

	@Override
	public boolean handleResult(Map<String, Object> map, int id) throws P4JavaException {
		String key = "submittedChange";
		if(map.containsKey(key)) {
			try {
				change = Long.parseLong((String) map.get(key));
			} catch (NumberFormatException e) {
				change = -1;
			}
		}
		return true;
	}

	public boolean isDone() {
		return done;
	}

	public long getChange() {
		return change;
	}

}