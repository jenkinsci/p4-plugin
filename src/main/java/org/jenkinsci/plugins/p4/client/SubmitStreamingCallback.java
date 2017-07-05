package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IServer;
import hudson.model.TaskListener;

import java.util.Map;

public class SubmitStreamingCallback extends AbstractStreamingCallback {

	public SubmitStreamingCallback(IServer iserver, TaskListener listener) {
		super(iserver, listener);
	}

	private long change = 0;

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

	public long getChange() {
		return change;
	}
}