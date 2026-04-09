package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IServer;
import com.perforce.p4java.server.callback.ICommandCallback;
import com.perforce.p4java.server.callback.IStreamingCallback;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.console.P4Logging;

import java.util.Map;

public abstract class AbstractStreamingCallback implements IStreamingCallback {

	private boolean done = false;
	private boolean fail = false;
	private P4JavaException exception = null;

	private final Server server;
	private final Validate validate;
	private final TaskListener listener;

	public AbstractStreamingCallback(IServer iserver, TaskListener listener) {
		this.server = (Server) iserver;
		this.listener = listener;
		this.validate = new Validate(listener);
	}

	@Override
	public boolean startResults(int key) {
		return true;
	}

	@Override
	public boolean endResults(int key) {
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

	protected void log(Map<String, Object> map) {
		if (listener == null) {
			return;
		}

		if (map == null || map.isEmpty() || map.get("depotFile") == null) {
			return;
		}

		// Skip logging if quiet is set in the P4Logging callback handler.
		if(isQuiet()) {
			return;
		}

		StringBuilder msg = new StringBuilder();
		String action = (map.get("action") == null) ? "" : (String) map.get("action");
		String clientFile = (map.get("clientFile") == null) ? "" : (String) map.get("clientFile");
		String depotFile = (map.get("depotFile") == null) ? "" : (String) map.get("depotFile");
		String rev = (map.get("rev") == null) ? "" : (String) map.get("rev");

		msg.append(depotFile + "#" + rev);
		msg.append(" - ");
		msg.append(clientFile + " ");
		msg.append(action);
		listener.getLogger().println(msg);
	}

	private boolean isQuiet() {
		// Have to fetch callback then reset it - need a getter in P4Java
		ICommandCallback callback = server.registerCallback(null);
		server.registerCallback(callback);

		if (callback instanceof P4Logging logging) {
			return logging.isQuiet();
		}
		return false;
	}

}
