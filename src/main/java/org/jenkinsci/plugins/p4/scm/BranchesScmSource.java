package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BranchesScmSource extends AbstractP4ScmSource {

	private P4Browser browser;
  	private String filter;

	@DataBoundConstructor
	public BranchesScmSource(String id, String credential, String includes, String charset, String format) {
		super(id, credential);
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

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), getCredential(), listener);

		String actualFilter = getFilter();
		if(getFilter() == null || filter.trim().equals("")){
			actualFilter = ".*";
		}
		Pattern filterPattern = Pattern.compile(actualFilter);

		List<IFileSpec> specs = p4.getDirs(paths);
		for (IFileSpec s : specs) {
			String branch = s.getOriginalPathString();

			// check the filters
			if(!filterPattern.matcher(branch).matches()){
				continue;
			}

			// get depotPath and check for null
			Path depotPath = Paths.get(branch);
			if (depotPath == null) {
				continue;
			}

			// get filename and check for null
			Path file = depotPath.getFileName();
			if (file == null) {
				continue;
			}

			P4Path p4Path = new P4Path(branch);
			P4Head head = new P4Head(file.toString(), Arrays.asList(p4Path));
			list.add(head);
		}
		p4.disconnect();

		return list;
	}

	public String getFilter() {
		return filter;
	}

	@Extension
	@Symbol("multiBranch")
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Helix Source";
		}
	}
}
