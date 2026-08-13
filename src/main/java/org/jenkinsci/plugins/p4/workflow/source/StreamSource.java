package org.jenkinsci.plugins.p4.workflow.source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class StreamSource extends AbstractSource {

	private final String stream;

	private String streamAtChange = StringUtils.EMPTY;

	@DataBoundConstructor
	public StreamSource(String stream) {
		this.stream = stream;
	}

	public String getStream() {
		return stream;
	}

	public String getStreamAtChange() {
		return streamAtChange;
	}

	@DataBoundSetter
	public void setStreamAtChange(String streamAtChange) {
		this.streamAtChange = streamAtChange;
	}

	@Override
	public Workspace getWorkspace(String charset, String format) {
		StreamWorkspaceImpl streamWorkspace = new StreamWorkspaceImpl(charset, false, stream, format);
		streamWorkspace.setStreamAtChange(streamAtChange);
		return streamWorkspace;
	}

	@Extension
	@Symbol("streamSource")
	public static final class DescriptorImpl extends P4SyncDescriptor {

		public DescriptorImpl() {
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Stream Codeline";
		}

		public AutoCompletionCandidates doAutoCompleteStream(@QueryParameter String value) {
			return WorkspaceDescriptor.doAutoCompleteStreamName(value);
		}

		public FormValidation doCheckStream(@QueryParameter String value) {
			return WorkspaceDescriptor.doCheckStreamName(value);
		}
	}
}
