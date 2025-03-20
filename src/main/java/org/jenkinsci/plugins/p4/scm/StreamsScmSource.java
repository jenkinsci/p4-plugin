package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IStreamSummary;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public class StreamsScmSource extends AbstractP4ScmSource {

	private P4Browser browser;

	@DataBoundConstructor
	public StreamsScmSource(String credential, String includes, String charset, String format) {
		super(credential);
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
	public List<P4SCMHead> getTags(@NonNull TaskListener listener) {
		return new ArrayList<>();
	}

	@Override
	public List<P4SCMHead> getHeads(@NonNull TaskListener listener) throws Exception {
		List<String> paths = getIncludePaths();
		HashSet<P4SCMHead> list = new HashSet<>();

		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {

			Pattern excludesPattern = Pattern.compile(getExcludes());

			List<IStreamSummary> specs = p4.getStreams(paths);
			for (IStreamSummary s : specs) {
				String name = s.getName();

				// check the excludes
				if (excludesPattern.matcher(name).matches()) {
					continue;
				}

				String stream = s.getStream();
				P4Path p4Path = new P4Path(stream);
				P4SCMHead head = new P4SCMHead(name, p4Path);
				list.add(head);
			}
		}

		return new ArrayList<>(list);
	}

	@Override
	public Workspace getWorkspace(P4Path path) {
		return new StreamWorkspaceImpl(getCharset(), false, path.getPath(), getFormat());
	}

	@Extension
	@Symbol("multiStreams")
	public static final class DescriptorImpl extends P4SCMSourceDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Helix Streams";
		}
	}
}
