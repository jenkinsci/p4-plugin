package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder;
import com.perforce.p4java.server.IServer;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.List;
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

		List<IFileSpec> specList = new ArrayList<>();
		specList.add(ResultListBuilder.handleFileReturn(map, getServer()));
		try {
			getValidate().check(specList, "");
		} catch (Exception e) {
			setFail();
			P4JavaException exception = new P4JavaException(e);
			setException(exception);
			// re-throw exception as AbortException is only used if !quiet
			throw exception;
		}
		return true;
	}

	public long getChange() {
		return change;
	}
}