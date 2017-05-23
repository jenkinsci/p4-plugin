package org.jenkinsci.plugins.p4.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IServer;
import com.perforce.p4java.server.callback.IStreamingCallback;

import hudson.model.TaskListener;

public class ReconcileStreamingCallback implements IStreamingCallback {

	private boolean done = false;

	private final Server server;
	private final Validate validate;

	public ReconcileStreamingCallback(IServer iserver, TaskListener listener) {
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
	public boolean handleResult(Map<String, Object> map, int key) throws P4JavaException {
		List<IFileSpec> specList = new ArrayList<IFileSpec>();
		specList.add(server.handleFileReturn(map));

		try {
			validate.check(specList, "also opened by", "no file(s) to reconcile", "must sync/resolve",
					"exclusive file already opened", "cannot submit from stream", "instead of", "empty, assuming text");
		} catch (Exception e) {
			// re-throw exception as AbortException is only used if !quiet
			throw new P4JavaException(e);
		}
		return true;
	}

	public boolean isDone() {
		return done;
	}
}