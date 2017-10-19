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
		List<IFileSpec> specList = new ArrayList<IFileSpec>();
		specList.add(ResultListBuilder.handleFileReturn(map, getServer()));

		try {
			getValidate().check(specList, "also opened by", "no file(s) to reconcile", "must sync/resolve",
					"exclusive file already opened", "cannot submit from stream", "instead of", "empty, assuming text");
		} catch (Exception e) {
			// re-throw exception as AbortException is only used if !quiet
			throw new P4JavaException(e);
		}
		return true;
	}
}