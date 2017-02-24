package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IStreamSummary;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StreamsScmSource extends AbstractP4ScmSource {

	@DataBoundConstructor
	public StreamsScmSource(String id, String credential, String includes, String charset, String format, P4Browser browser) {
		super(id, credential, includes, charset, format, browser);
	}

	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(credential, listener);
		try {
			List<IStreamSummary> specs = p4.getStreams(paths);
			for (IStreamSummary s : specs) {
				String name = s.getName();
				String stream = s.getStream();
				P4Head head = new P4Head(name, stream, true);
				list.add(head);
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<>(list);
	}

	@Override
	public Workspace getWorkspace(String path) {
		return new StreamWorkspaceImpl(getCharset(), false, path, getFormat());
	}

	@Extension
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "BETA! Perforce Streams";
		}
	}
}
