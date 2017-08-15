package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.scm.swarm.P4Path;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class BranchesScmSource extends AbstractP4ScmSource {

	private P4Browser browser;

	@DataBoundConstructor
	public BranchesScmSource(String id, String credential, String includes, String charset, String format) {
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
	public List<P4Head> getTags(@NonNull TaskListener listener) throws Exception {
		return new ArrayList<>();
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<P4Head> list = new HashSet<P4Head>();

		ConnectionHelper p4 = new ConnectionHelper(getOwner(), getCredential(), listener);

		List<IFileSpec> specs = p4.getDirs(paths);
		for (IFileSpec s : specs) {
			String branch = s.getOriginalPathString();
			P4Path p4Path = new P4Path(branch);

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

			P4Head head = new P4Head(file.toString(), Arrays.asList(p4Path));
			list.add(head);
		}
		p4.disconnect();

		return new ArrayList<>(list);
	}



	@Extension
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Helix Branches";
		}
	}
}
