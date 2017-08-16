package org.jenkinsci.plugins.p4.workflow.source;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

public class StreamSource extends AbstractSource {

	private final String stream;

	@DataBoundConstructor
	public StreamSource(String stream) {
		this.stream = stream;
	}

	public String getStream() {
		return stream;
	}

	@Override
	public Workspace getWorkspace(String charset, String format) {
		return new StreamWorkspaceImpl(charset, false, stream, format);
	}

	@Extension
	@Symbol("streamSource")
	public static final class DescriptorImpl extends P4SyncDescriptor {

		public DescriptorImpl() {
		}

		@Override
		public String getDisplayName() {
			return "Stream Codeline";
		}
	}
}
