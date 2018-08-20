package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class BranchesScmSource extends AbstractP4ScmSource {

	private P4Browser browser;
	private String filter = DescriptorImpl.defaultFilter;
	private String mappings = DescriptorImpl.defaultPath;

	@DataBoundConstructor
	public BranchesScmSource(String credential, String includes, String charset, String format) {
		super(credential);
		setIncludes(includes);
		setCharset(charset);
		setFormat(format);
	}

	@DataBoundSetter
	public void setBrowser(P4Browser browser) {
		this.browser = browser;
	}

	@DataBoundSetter
	public void setFilter(String filter) {
		this.filter = filter;
	}

	public String getFilter() {
		return filter;
	}

	public String getMappings() {
		// support 1.8.1 configurations that did not have any mappings
		if (mappings == null) {
			mappings = DescriptorImpl.defaultPath;
		}

		return mappings;
	}

	@DataBoundSetter
	public void setMappings(String mappings) {
		this.mappings = mappings;
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
		List<P4Head> list = new ArrayList<>();

		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), getCredential(), listener)) {
			String actualFilter = getFilter();
			if (getFilter() == null || filter.trim().equals("")) {
				actualFilter = ".*";
			}
			Pattern filterPattern = Pattern.compile(actualFilter);

			List<IFileSpec> specs = p4.getDirs(paths);
			for (IFileSpec s : specs) {
				String branch = s.getOriginalPathString();

				// check the branch is not empty
				if (branch == null || branch.isEmpty()) {
					continue;
				}

				// check the filters
				if (!filterPattern.matcher(branch).matches()) {
					continue;
				}

				// get filename and check for null
				String file = branch.substring(branch.lastIndexOf("/") + 1);
				if (file == null || file.isEmpty()) {
					continue;
				}

				P4Path p4Path = new P4Path(branch);
				P4Head head = new P4Head(file, p4Path);
				list.add(head);
			}
		}

		return list;
	}

	@Override
	public Workspace getWorkspace(P4Path path) {
		if (path == null) {
			throw new IllegalArgumentException("missing branch path");
		}

		List<String> views = new ArrayList<>();

		for (String mapping : getViewMappings()) {
			StringBuffer sb = new StringBuffer();
			sb.append(path.getPath());
			sb.append("/");
			sb.append(mapping);
			views.add(sb.toString());
		}

		String client = getFormat();
		String jenkinsPath = path.getPath() + "/" + getScriptPathOrDefault();
		String jenkinsView = ViewMapHelper.getClientView(jenkinsPath, client);
		String mappingsView = ViewMapHelper.getClientView(views, client);
		String view = jenkinsView + "\n" + mappingsView;

		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	private List<String> getViewMappings() {
		return toLines(getMappings());
	}

	@Extension
	@Symbol("multiBranch")
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		public static final String defaultPath = "...";

		public static final String defaultFilter = ".*";

		@Override
		public String getDisplayName() {
			return "Helix Branches";
		}
	}
}
