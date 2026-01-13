package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder;
import com.perforce.p4java.server.IServer;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReconcileStreamingCallback extends AbstractStreamingCallback {

	public ReconcileStreamingCallback(IServer iserver, TaskListener listener) {
		super(iserver, listener);
	}

	@Override
	public boolean handleResult(Map<String, Object> map, int key) throws P4JavaException {
		log(map);

		List<IFileSpec> specList = new ArrayList<>();
		specList.add(ResultListBuilder.handleFileReturn(map, getServer()));

		try {
			getValidate().check(specList, "also opened by", "no file(s) to reconcile", "must sync/resolve",
					"exclusive file already opened", "cannot submit from stream", "instead of", "empty, assuming text");
		} catch (Exception e) {
			setFail();
			P4JavaException exception = new P4JavaException(e);
			setException(exception);
			// re-throw exception as AbortException is only used if !quiet
			throw exception;
		}
		return true;
	}
}