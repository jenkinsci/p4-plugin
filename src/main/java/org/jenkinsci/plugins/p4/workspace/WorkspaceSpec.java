package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.client.IClientSummary.ClientLineEnd;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

public class WorkspaceSpec extends AbstractDescribableImpl<WorkspaceSpec> implements Serializable {

	private static final long serialVersionUID = 1L;

	public final boolean allwrite;
	public final boolean clobber;
	public final boolean compress;
	public final boolean locked;
	public final boolean modtime;
	public final boolean rmdir;

	private final String streamName;
	private final String line;
	private final String view;
	private final String changeView;
	private final String type;

	private final String serverID;
	private final boolean backup;

	public String getStreamName() {
		return streamName;
	}

	public String getLine() {
		return line;
	}

	public String getView() {
		return view;
	}

	public String getChangeView() {
		return changeView;
	}

	public String getType() {
		return type;
	}

	public String getServerID() {
		return serverID;
	}

	public boolean isBackup() {
		return backup;
	}

	@DataBoundConstructor
	public WorkspaceSpec(boolean allwrite, boolean clobber, boolean compress,
	                     boolean locked, boolean modtime, boolean rmdir, String streamName,
	                     String line, String view, String changeView, String type,
	                     String serverID, boolean backup) {
		this.allwrite = allwrite;
		this.clobber = clobber;
		this.compress = compress;
		this.locked = locked;
		this.modtime = modtime;
		this.rmdir = rmdir;
		this.streamName = streamName;
		this.line = line;
		this.view = view;
		this.changeView = changeView;
		this.type = type;
		this.serverID = serverID;
		this.backup = backup;
	}

	// Default setup for Classic Workspace
	public WorkspaceSpec(String view, String changeView) {
		this.allwrite = false;
		this.clobber = false;
		this.compress = false;
		this.locked = false;
		this.modtime = false;
		this.rmdir = false;
		this.streamName = null;
		this.line = "LOCAL";
		this.view = view;
		this.changeView = changeView;
		this.type = null;
		this.serverID = null;
		this.backup = true;
	}

	@Deprecated
	public WorkspaceSpec(boolean allwrite, boolean clobber, boolean compress,
	                     boolean locked, boolean modtime, boolean rmdir, String streamName,
	                     String line, String view) {
		this(allwrite, clobber, compress, locked, modtime, rmdir, streamName, line, view, null, null, null, true);
	}

	@Extension
	@Symbol("clientSpec")
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

		public ListBoxModel doFillTypeItems() {
			ListBoxModel list = new ListBoxModel();
			for (WorkspaceSpecType type : WorkspaceSpecType.values()) {
				list.add(type.name());
			}
			return list;
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 *
		 * @param value The text that the user entered.
		 * @return suggestion
		 */
		public AutoCompletionCandidates doAutoCompleteStreamName(
				@QueryParameter String value) {

			return StreamDescImpl.doAutoCompleteStreamName(value);
		}
	}
}
