package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GlobalLibraryScmSource extends AbstractP4ScmSource {

	private final String path;

	public String getPath() {
		return path;
	}

	@DataBoundConstructor
	public GlobalLibraryScmSource(String id, String credential, String charset, String path) {
		super(id, credential);
		this.path = path;
		setCharset(charset);
		setFormat("jenkins-library");
	}

	@Override
	protected SCMRevision retrieve(@NonNull final String thingName, @NonNull TaskListener listener)
			throws IOException, InterruptedException {
		try {
			P4Path p4Path = new P4Path(path, thingName);
			P4Head head = new P4Head(thingName, Arrays.asList(p4Path));
			SCMRevision revision = getRevision(head, listener);
			return revision;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	// Not used for Global Library
	@Override
	public P4Browser getBrowser() {
		return null;
	}

	@Override
	public List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception {
		// not used
		return new ArrayList<>();
	}

	@Override
	public List<P4Head> getTags(@NonNull TaskListener listener) throws Exception {
		// not used
		return new ArrayList<>();
	}

	@Override
	public Workspace getWorkspace(List<P4Path> paths) {
		String client = getFormat();

		StringBuffer sb = new StringBuffer();
		for (P4Path path : paths) {
			String view = path.getPath() + "/..." + " //" + client + "/...";
			sb.append(view).append("\n");
		}

		WorkspaceSpec spec = new WorkspaceSpec(sb.toString(), null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	@Override
	public PerforceScm build(SCMHead head, SCMRevision revision) {
		if (head instanceof P4Head && revision instanceof P4Revision) {
			P4Head perforceHead = (P4Head) head;
			P4Revision perforceRevision = (P4Revision) revision;

			// Build workspace from 'head' paths
			List<P4Path> paths = perforceHead.getPaths();
			Workspace workspace = getWorkspace(paths);

			// Build populate from revision
			String pin = perforceRevision.getRef().toString();
			Populate populate = new GraphHybridImpl(true, pin, null);
			PerforceScm scm = new PerforceScm(getCredential(), workspace, null, populate, getBrowser());
			return scm;
		} else {
			throw new IllegalArgumentException("SCMHead and/or SCMRevision not a Perforce instance!");
		}
	}

	@Extension
	@Symbol("globalLib")
	public static final class DescriptorImpl extends P4ScmSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Helix Library";
		}
	}
}
