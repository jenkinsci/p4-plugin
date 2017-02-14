package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BranchesScmSource extends AbstractP4ScmSource {

	@DataBoundConstructor
	public BranchesScmSource(String id, String credential, String includes, String charset, String format, P4Browser browser) {
		super(id, credential, includes, charset, format, browser);
	}

	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(credential, listener);
		try {
			List<IFileSpec> specs = p4.getDirs(paths);
			for (IFileSpec s : specs) {
				String branch = s.getOriginalPathString();
				Path depotPath = Paths.get(branch);
				String name = depotPath.getFileName().toString();

				P4Head head = new P4Head(name, branch, false);
				list.add(head);
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
			return "Perforce Branches";
		}
	}
}
