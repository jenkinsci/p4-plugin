package org.jenkinsci.plugins.p4.workspace;

import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.core.IStream;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.option.server.GetStreamsOptions;
import com.perforce.p4java.server.IOptionsServer;

public class StreamDescImpl {
	
	static public AutoCompletionCandidates doAutoCompleteStreamName(
			@QueryParameter String value) {

		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 1) {
				List<String> streamPaths = new ArrayList<String>();
				streamPaths.add(value + "...");
				GetStreamsOptions opts = new GetStreamsOptions();
				opts.setMaxResults(10);
				List<IStreamSummary> list = iserver.getStreams(streamPaths,
						opts);
				for (IStreamSummary l : list) {
					c.add(l.getStream());
				}
			}
		} catch (Exception e) {
		}

		return c;
	}

	static public FormValidation doCheckStreamName(@QueryParameter String value) {
		try {
			IOptionsServer p4 = ConnectionFactory.getConnection();
			IStream stream = p4.getStream(value);
			if (stream != null) {
				return FormValidation.ok();
			}
			return FormValidation.warning("Unknown Stream: " + value);
		} catch (Exception e) {
			return FormValidation.error(e.getMessage());
		}
	}

	static public FormValidation doCheckFormat(@QueryParameter final String value) {
		if (value == null || value.isEmpty())
			return FormValidation
					.error("Workspace Name format is mandatory.");

		if (value.contains("${") && value.contains("}")) {
			return FormValidation.ok();
		}
		return FormValidation.error("Workspace Name format error.");
	}
}
