package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import org.antlr.v4.runtime.misc.NotNull;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractP4ScmSource extends SCMSource {

	protected final String credential;

	@NonNull
	private List<SCMSourceTrait> traits = new ArrayList<>();

	private String includes;
	private String charset;
	private String format;
	private Populate populate;

	public AbstractP4ScmSource(String credential) {
		this.credential = credential;
	}

	@DataBoundSetter
	public void setFormat(String format) {
		this.format = format;
	}

	@DataBoundSetter
	public void setPopulate(Populate populate) {
		this.populate = populate;
	}

	@DataBoundSetter
	public void setIncludes(String includes) {
		this.includes = includes;
	}

	@DataBoundSetter
	public void setCharset(String charset) {
		this.charset = charset;
	}

	@DataBoundSetter
	public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
		this.traits = new ArrayList<>(Util.fixNull(traits));
	}

	public String getCredential() {
		return credential;
	}

	@NonNull
	public List<SCMSourceTrait> getTraits() {
		return Collections.unmodifiableList(traits);
	}

	public String getIncludes() {
		return includes;
	}

	public String getCharset() {
		return charset;
	}

	public String getFormat() {
		return format;
	}

	public Populate getPopulate() {
		return populate;
	}

	public abstract P4Browser getBrowser();

	public abstract List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception;

	public abstract List<P4Head> getTags(@NonNull TaskListener listener) throws Exception;

	public Workspace getWorkspace(P4Path path) {
		if (path == null) {
			throw new IllegalArgumentException("missing path");
		}

		StringBuffer depotView = new StringBuffer();
		depotView.append(path.getPath());
		depotView.append("/...");

		String client = getFormat();
		String view = ViewMapHelper.getClientView(depotView.toString(), client);
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	public String getScriptPathOrDefault() {
		SCMSourceOwner owner = getOwner();
		if (owner instanceof WorkflowMultiBranchProject) {
			WorkflowMultiBranchProject branchProject = (WorkflowMultiBranchProject) owner;
			WorkflowBranchProjectFactory branchProjectFactory = (WorkflowBranchProjectFactory) branchProject.getProjectFactory();
			return branchProjectFactory.getScriptPath();
		}
		return "Jenkinsfile";
	}

	@Override
	protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {

		// TODO use payload to get change if trigger event
		if (event != null) {
			Object payload = event.getPayload();
		}

		try {
			List<P4Head> heads = getHeads(listener);

			List<P4Head> tags = getTags(listener);
			heads.addAll(tags);

			for (P4Head head : heads) {
				// null criteria means that all branches match.
				if (criteria == null) {
					// get revision and add observe
					SCMRevision revision = getRevision(head, listener);
					observer.observe(head, revision);
				} else {
					try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {
						SCMSourceCriteria.Probe probe = new P4Probe(p4, head);
						if (criteria.isHead(probe, listener)) {
							// get revision and add observe
							SCMRevision revision = getRevision(head, listener);
							observer.observe(head, revision);
						}
					}
				}
				// check for user abort
				checkInterrupt();
			}
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	@NotNull
	@Override
	protected SCMProbe createProbe(@NonNull SCMHead head, @CheckForNull SCMRevision revision) throws IOException {
		return newProbe(head, revision);
	}

	@Override
	public PerforceScm build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
		if (traits != null) {
			return new P4ScmBuilder(this, head, revision).withTraits(traits).build();
		} else {
			return new P4ScmBuilder(this, head, revision).build();
		}
	}

	/**
	 * SCMSource level action. `jenkins.branch.MetadataAction`
	 * @param event
	 * @param listener
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@NonNull
	@Override
	protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event, @NonNull TaskListener listener)
			throws IOException, InterruptedException {
		List<Action> result = new ArrayList<>();

		return result;
	}

	/**
	 * SCMHead level action.
	 *
	 * @param head
	 * @param event
	 * @param listener
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@NonNull
	@Override
	protected List<Action> retrieveActions(@NonNull SCMHead head, @CheckForNull SCMHeadEvent event, @NonNull TaskListener listener) throws IOException, InterruptedException {
		List<Action> result = new ArrayList<>();

		return result;

	/**
	 * SCMRevision level action.
	 *
	 * @param revision
	 * @param event
	 * @param listener
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@NonNull
	@Override
	protected List<Action> retrieveActions(@NonNull SCMRevision revision, @CheckForNull SCMHeadEvent event, @NonNull TaskListener listener) throws IOException, InterruptedException {
		List<Action> result = new ArrayList<>();

		return result;
	}

	/**
	 * Enable specific SCMHeadCategory categories.
	 * <p>
	 * TagSCMHeadCategory: Branches, Streams, Swarm and Graph
	 * ChangeRequestSCMHeadCategory: Swarm and Graph
	 *
	 * @param category
	 * @return
	 */
	@Override
	protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
		if (category instanceof ChangeRequestSCMHeadCategory) {
			return true;
		}
		if (category instanceof TagSCMHeadCategory) {
			return true;
		}
		return true;
	}

	public List<String> getIncludePaths() {
		return toLines(includes);
	}

	protected List<String> toLines(String value) {
		if (value == null) {
			return new ArrayList<>();
		}
		String[] array = value.split("[\\r\\n]+");
		return Arrays.asList(array);
	}

	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {

			// TODO look for graph revisions too

			long change = -1;
			P4Path path = head.getPath();
			String rev = path.getRevision();
			rev = (rev != null && !rev.isEmpty()) ? "/...@" + rev : "/...";
			long c = p4.getHead(path.getPath() + rev);
			change = (c > change) ? c : change;

			P4Revision revision = new P4Revision(head, new P4ChangeRef(change));
			return revision;
		}
	}
}
