package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.graph.IGraphRef;
import com.perforce.p4java.option.server.GraphShowRefOptions;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.util.NonLocalizable;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.PopulateDescriptor;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;

public class GraphScmSource extends AbstractP4ScmSource {

	private P4Browser browser;

	@DataBoundConstructor
	public GraphScmSource(String credential, String includes, String charset, String format) {
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
	public List<P4SCMHead> getTags(@NonNull TaskListener listener) throws Exception {

		List<P4SCMHead> list = new ArrayList<>();
		List<String> includes = getIncludePaths();

		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {
			for (String inc : includes) {
				List<IRepo> repos = p4.listRepos(inc);
				list.addAll(getRefsFromRepos(repos, p4));
			}
		}
		return list;
	}

	@Override
	public List<P4SCMHead> getHeads(@NonNull TaskListener listener) throws Exception {

		List<P4SCMHead> list = new ArrayList<>();
		List<String> includes = getIncludePaths();

		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {
			for (String inc : includes) {
				List<IRepo> repos = p4.listRepos(inc);
				list.addAll(getBranchesFromRepos(repos, p4));
			}
		}
		return list;
	}

	private List<P4SCMHead> getBranchesFromRepos(List<IRepo> repos, ConnectionHelper p4) throws Exception {
		List<P4SCMHead> list = new ArrayList<>();

		for (IRepo repo : repos) {
			String repoName = getRepoName(repo);
			List<IGraphRef> refs = getRefs(p4, repoName, "branch");

			for (IGraphRef ref : refs) {
				String branchName = ref.getName();
				P4Path p4Path = new P4Path(repoName);
				p4Path.setRevision(branchName);
				String name = p4Path.getName();

				P4SCMHead head = new P4SCMHead(name, p4Path);
				list.add(head);
			}
		}
		return list;
	}

	private List<P4GraphRequestSCMHead> getRefsFromRepos(List<IRepo> repos, ConnectionHelper p4) throws Exception {
		List<P4GraphRequestSCMHead> list = new ArrayList<>();

		for (IRepo repo : repos) {
			String repoName = getRepoName(repo);
			List<IGraphRef> refs = getRefs(p4, repoName, "ref");

			for (IGraphRef ref : refs) {
				String branchName = ref.getName();

				// only process 'merge'
				if (!branchName.endsWith("/merge")) {
					continue;
				}

				P4Path p4Path = new P4Path(repoName);
				p4Path.setRevision(branchName);
				String name = p4Path.getName();

				P4SCMHead target = new P4SCMHead(name, p4Path);
				P4GraphRequestSCMHead tag = new P4GraphRequestSCMHead(name, repoName, branchName, p4Path, target);
				list.add(tag);
			}
		}
		return list;
	}

	private String getRepoName(IRepo repo) {
		String repoName = repo.getName();
		if (repoName.endsWith(".git")) {
			repoName = repoName.substring(0, repoName.lastIndexOf(".git"));
		}
		return repoName;
	}

	private List<IGraphRef> getRefs(ConnectionHelper p4, String repoName, String type) throws P4JavaException {
		GraphShowRefOptions opts = new GraphShowRefOptions();
		opts.setType(type);
		opts.setRepo(repoName);
		List<IGraphRef> refs = p4.getConnection().getGraphShowRefs(opts);

		return refs;
	}

	@Override
	public P4SCMRevision getRevision(TempClientHelper p4, P4SCMHead head) {
		P4Ref ref = p4.getGraphHead(head.getPath().getPath());
		P4SCMRevision revision = new P4SCMRevision(head, ref);
		return revision;
	}

	@Override
	public Workspace getWorkspace(P4Path path) {
		if (path == null) {
			throw new IllegalArgumentException("missing path");
		}

		StringBuilder depotView = new StringBuilder();
		depotView.append(path.getPath());
		depotView.append("/...");

		String client = getFormat();
		String view = ViewMapHelper.getClientView(depotView.toString(), client, false);
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec, false);
	}

	@Extension
	@Symbol("multiGraph")
	public static final class DescriptorImpl extends P4SCMSourceDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Helix4Git";
		}

		@NonNull
		@Override
		protected SCMHeadCategory[] createCategories() {
			return new SCMHeadCategory[]{
					new UncategorizedSCMHeadCategory(new NonLocalizable("Branches")),
					new ChangeRequestSCMHeadCategory(new NonLocalizable("Reviews"))
			};
		}

		public List getGraphPopulateDescriptors() {
			Jenkins j = Jenkins.get();
			DescriptorExtensionList<Populate, Descriptor<Populate>> list = j.getDescriptorList(Populate.class);
			for (Descriptor<Populate> d : list) {
				if (!(d instanceof PopulateDescriptor p)) {
					list.remove(d);
				} else {
					if (!p.isGraphCompatible()) {
						list.remove(p);
					}
				}
			}
			return list;
		}
	}
}