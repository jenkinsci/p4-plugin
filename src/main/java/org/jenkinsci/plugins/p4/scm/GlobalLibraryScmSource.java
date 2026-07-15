package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.NavigateHelper;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GlobalLibraryScmSource extends AbstractP4ScmSource {

	private final String path;

	public String getPath() {
		return path;
	}

	@DataBoundConstructor
	public GlobalLibraryScmSource(String credential, String charset, String path) {
		super(credential);
		this.path = path;
		setCharset(charset);
	}

	@Override
	protected SCMRevision retrieve(@NonNull final String thingName, @NonNull TaskListener listener)
			throws IOException {

		P4Path p4Path = new P4Path(path);
		Workspace workspace = getWorkspace(p4Path);

		try (TempClientHelper p4 = new TempClientHelper(getOwner(), credential, listener, workspace)) {
			p4Path.setRevision(thingName);
			P4SCMHead head = new P4SCMHead(thingName, p4Path);
			SCMRevision revision = getRevision(p4, head);
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
	public List<P4SCMHead> getHeads(@NonNull TaskListener listener) {
		// not used
		return new ArrayList<>();
	}

	@Override
	public List<P4SCMHead> getTags(@NonNull TaskListener listener) {
		// not used
		return new ArrayList<>();
	}

	@NonNull
	@Override
	public synchronized PerforceScm build(@NonNull SCMHead head, SCMRevision revision) {
		if (head instanceof P4SCMHead perforceHead && revision instanceof P4SCMRevision perforceRevision) {

			// Build workspace from 'head' paths
			P4Path path = perforceHead.getPath();
			Workspace workspace = getWorkspace(path);

			// Build populate from revision
			String pin = perforceRevision.getRef().toString();
			Populate populate = new GraphHybridImpl(true, pin, null);
			PerforceScm scm = new PerforceScm(getCredential(), workspace, null, populate, getBrowser());
			return scm;
		} else {
			throw new IllegalArgumentException("SCMHead and/or SCMRevision not a Perforce instance!");
		}
	}

	@Override
	public Workspace getWorkspace(P4Path path) {
		if (path == null) {
			throw new IllegalArgumentException("missing path");
		}

		setFormat("jenkins-lib-${NODE_NAME}-${JOB_NAME}-${BUILD_NUMBER}-${EXECUTOR_NUMBER}");

		// patch for older configuration version when '/...' was not required.
		String depotView = path.getPath();
		if(!depotView.endsWith("/...")) {
			depotView += "/...";
		}

		String client = getFormat();
		String view = ViewMapHelper.getClientView(depotView, client, true);

		// Make a workspace spec that is all default except for make it writeable.
		// Without that, we can't do Replay in pipelines.
		boolean allwrite = true;	// the change
		boolean clobber = true;
		boolean compress = false;
		boolean locked = false;
		boolean modtime = false;
		boolean rmdir = false;
		String streamName = null;
		String line = "LOCAL";
		String changeView = null;
		String type = null;
		String serverID = null;
		boolean backup = true;
		WorkspaceSpec spec = new WorkspaceSpec(
			allwrite, clobber, compress,
			locked,  modtime,  rmdir,  streamName,
			line,  view,  changeView,  type,
			serverID,  backup );

		return new ManualWorkspaceImpl(getCharset(), false, client, spec, true);
	}

	@Extension
	@Symbol("globalLib")
	public static final class DescriptorImpl extends P4SCMSourceDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Helix Library";
		}

		public FormValidation doCheckPath(@QueryParameter String value) {
			if (value == null || value.isEmpty() || !value.endsWith("/...")) {
				return FormValidation.error("Please provide a valid Depot path e.g. //depot/libs/...");
			}
			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompletePath(@QueryParameter String value) {
			NavigateHelper nav = new NavigateHelper(10);
			return nav.getCandidates(value);
		}

		@Override
		public boolean isApplicable(Class<? extends SCMSourceOwner> owner) {
			return false;
		}
	}
}
