package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IServer;
import com.perforce.p4java.server.callback.IStreamingCallback;
import hudson.model.TaskListener;

import java.util.Map;

public abstract class AbstractStreamingCallback implements IStreamingCallback {

	private boolean done = false;
	private boolean fail = false;
	private P4JavaException exception = null;

	private final Server server;
	private final Validate validate;

	public AbstractStreamingCallback(IServer iserver, TaskListener listener) {
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
	public abstract boolean handleResult(Map<String, Object> map, int key) throws P4JavaException;

	public boolean isDone() {
		return done;
	}

	public boolean isFail() {
		return fail;
	}

	public void setFail() {
		fail = true;
	}

	public P4JavaException getException() {
		return exception;
	}

	public void setException(P4JavaException exception) {
		this.exception = exception;
	}

	public Server getServer() {
		return server;
	}

	public Validate getValidate() {
		return validate;
	}
}
