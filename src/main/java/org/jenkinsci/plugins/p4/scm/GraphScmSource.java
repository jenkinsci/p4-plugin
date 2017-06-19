package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IRepo;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class GraphScmSource extends AbstractP4ScmSource {

	private P4Browser browser;

	@DataBoundConstructor
	public GraphScmSource(String id, String credential, String includes, String charset, String format) {
		super(id, credential, charset, format);
		setIncludes(includes);
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
	public List<P4ChangeRequestSCMHead> getTags(@NonNull TaskListener listener) throws Exception {
		return new ArrayList<>();
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> includes = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener);
		try {
			for (String inc : includes) {
				List<IRepo> repos = p4.listRepos(inc);
				for (IRepo r : repos) {
					String path = r.getName();
					if (path.endsWith(".git")) {
						path = path.substring(0, path.lastIndexOf(".git"));
					}
					String name = path.substring(path.lastIndexOf("/") + 1);
					P4Head head = new P4Head(name, Arrays.asList(path), false);
					list.add(head);
				}
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<>(list);
	}

	@Override
	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		try (ClientHelper p4 = new ClientHelper(getOwner(), credential, listener, scmSourceClient, getCharset())) {
			long change = -1;

			P4Ref ref = p4.getGraphHead(head.getPaths().get(0));
			if (ref instanceof P4GraphRef) {
				P4GraphRef graphHead = (P4GraphRef) ref;
				change = graphHead.getDate();
			}

			P4Revision revision = new P4Revision(head, change);
			return revision;
		}
	}

	@Extension
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce Graph";
		}
	}
}