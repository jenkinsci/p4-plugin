package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IRepo;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GraphScmSource extends AbstractP4ScmSource {

	@DataBoundConstructor
	public GraphScmSource(String id, String credential, String includes, String charset, String format, P4Browser browser) {
		super(id, credential, includes, charset, format, browser);
	}

	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> includes = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener);
		try {
			for (String inc : includes) {
				List<IRepo> repos = p4.listRepos(inc);
				for (IRepo r : repos) {
					String path = r.getName();
					if(path.endsWith(".git")) {
						path = path.substring(0, path.lastIndexOf(".git"));
					}
					String name = path.substring(path.lastIndexOf("/") + 1);
					P4Head head = new P4Head(name, path, false);
					list.add(head);
				}
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<>(list);
	}

	@Override
	public Workspace getWorkspace(String path) {
		String client = getFormat();
		String view = path + "/..." + " //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, null, "LOCAL", view);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	@Extension
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Graph";
		}
	}
}