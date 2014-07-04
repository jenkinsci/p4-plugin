package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.client.IClientSummary.ClientLineEnd;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.option.server.GetStreamsOptions;
import com.perforce.p4java.server.IOptionsServer;

public class WorkspaceSpec extends AbstractDescribableImpl<WorkspaceSpec> {

	public final boolean allwrite;
	public final boolean clobber;
	public final boolean compress;
	public final boolean locked;
	public final boolean modtime;
	public final boolean rmdir;

	private final String streamName;
	private final String line;
	private final String view;

	public String getStream() {
		return streamName;
	}

	public String getLine() {
		return line;
	}

	public String getView() {
		return view;
	}

	@DataBoundConstructor
	public WorkspaceSpec(boolean allwrite, boolean clobber, boolean compress,
			boolean locked, boolean modtime, boolean rmdir, String streamName,
			String line, String view) {
		this.allwrite = allwrite;
		this.clobber = clobber;
		this.compress = compress;
		this.locked = locked;
		this.modtime = modtime;
		this.rmdir = rmdir;
		this.streamName = streamName;
		this.line = line;
		this.view = view;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<WorkspaceSpec> {

		@Override
		public String getDisplayName() {
			return "Perforce Client Spec";
		}

		public ListBoxModel doFillLineItems() {
			ListBoxModel list = new ListBoxModel();
			for (ClientLineEnd end : IClientSummary.ClientLineEnd.values()) {
				list.add(end.name());
			}
			return list;
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 * 
		 * @param value
		 *            The text that the user entered.
		 */
		public AutoCompletionCandidates doAutoCompleteStreamName(
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
	}
}
