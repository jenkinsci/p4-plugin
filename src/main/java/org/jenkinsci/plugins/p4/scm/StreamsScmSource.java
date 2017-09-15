package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IStreamSummary;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.scm.swarm.P4Path;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class StreamsScmSource extends AbstractP4ScmSource {

	private P4Browser browser;

	@DataBoundConstructor
	public StreamsScmSource(String id, String credential, String includes, String charset, String format) {
		super(id, credential);
		setIncludes(includes);
		setCharset(charset);
		setFormat(format);
	}

	@DataBoundSetter
	public void setBrowser(P4Browser browser) {
		this.browser = browser;
	}

	@Override
	public P4Browser getBrowser() {
		return browser;
	}

	@Override
	public List<P4Head> getTags(@NonNull TaskListener listener) throws Exception {
		return new ArrayList<>();
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener);
		try {
			List<IStreamSummary> specs = p4.getStreams(paths);
			for (IStreamSummary s : specs) {
				String name = s.getName();
				String stream = s.getStream();
				P4Path p4Path = new P4Path(stream);
				P4Head head = new P4Head(name, Arrays.asList(p4Path));
				list.add(head);
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<>(list);
	}

	@Override
	public Workspace getWorkspace(List<P4Path> paths) {
		return new StreamWorkspaceImpl(getCharset(), false, paths.get(0).getPath(), getFormat());
	}

	@Extension
	@Symbol("multiStreams")
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Helix Streams";
		}
	}
}
