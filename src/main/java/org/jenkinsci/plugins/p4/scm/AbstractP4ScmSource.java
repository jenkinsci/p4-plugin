package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractP4ScmSource extends SCMSource {

	private static Logger logger = Logger.getLogger(AbstractP4ScmSource.class.getName());
	public static final String scmSourceClient = "jenkins-master";

	protected final String credential;

	private String includes;
	private String charset;
	private String format;
	private Populate populate;

	public AbstractP4ScmSource(String id, String credential) {
		super(id);
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

	public String getCredential() {
		return credential;
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

	public Workspace getWorkspace(List<P4Path> paths) {
		String client = getFormat();

		String scriptPath = getScriptPathOrDefault("Jenkinsfile");
		StringBuffer sb = new StringBuffer();
		for (P4Path path : paths) {
			String view = String.format("%s/%s //%s/%s", path.getPath(), scriptPath, client, scriptPath);
			sb.append(view).append("\n");
		}

		WorkspaceSpec spec = new WorkspaceSpec(sb.toString(), null);
		return new ManualWorkspaceImpl(getCharset(), false, client, spec);
	}

	protected String getScriptPathOrDefault(String defaultScriptPath) {
		SCMSourceOwner owner = getOwner();
		if(owner instanceof WorkflowMultiBranchProject){
			WorkflowMultiBranchProject branchProject = (WorkflowMultiBranchProject) owner;
			WorkflowBranchProjectFactory branchProjectFactory = (WorkflowBranchProjectFactory) branchProject.getProjectFactory();
			return branchProjectFactory.getScriptPath();
		}
		return defaultScriptPath;
	}

	@Override
	public PerforceScm build(SCMHead head, SCMRevision revision) {
		if (head instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead perforceTag = (P4ChangeRequestSCMHead) head;
			List<P4Path> paths = perforceTag.getPaths();
			Workspace workspace = getWorkspace(paths);
			PerforceScm scm = new PerforceScm(getCredential(), workspace, null, getPopulate(), getBrowser());

			P4Review review = new P4Review(head.getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
			return scm;
		}
		if (head instanceof P4GraphRequestSCMHead) {
			P4GraphRequestSCMHead graphTag = (P4GraphRequestSCMHead) head;
			List<P4Path> paths = graphTag.getPaths();
			Workspace workspace = getWorkspace(paths);
			PerforceScm scm = new PerforceScm(getCredential(), workspace, null, getPopulate(), getBrowser());
			return scm;
		}
		if (head instanceof P4Head) {
			P4Head perforceHead = (P4Head) head;
			List<P4Path> paths = perforceHead.getPaths();
			Workspace workspace = getWorkspace(paths);
			PerforceScm scm = new PerforceScm(getCredential(), workspace, null, getPopulate(), getBrowser());
			return scm;
		}
		throw new IllegalArgumentException("SCMHead not a Perforce instance!");
	}

	@Override
	protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {
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
					ClientHelper p4 = new ClientHelper(getOwner(), credential, listener, scmSourceClient, charset);
					SCMSourceCriteria.Probe probe = new P4Probe(p4, head);
					if (criteria.isHead(probe, listener)) {
						// get revision and add observe
						SCMRevision revision = getRevision(head, listener);
						observer.observe(head, revision);
					}
				}
				// check for user abort
				checkInterrupt();
			}
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	protected List<String> getIncludePaths() {
		return toLines(includes);
	}

	protected List<String> toLines(String value) {
		if(value == null) {
			return new ArrayList<>();
		}
		String[] array = value.split("[\\r\\n]+");
		return Arrays.asList(array);
	}

	public P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		try (ClientHelper p4 = new ClientHelper(getOwner(), credential, listener, scmSourceClient, charset)) {

			// TODO look for graph revisions too

			long change = -1;
			for (P4Path path : head.getPaths()) {
				String rev = path.getRevision();
				rev = (rev != null && !rev.isEmpty()) ? "/...@" + rev : "/...";
				long c = p4.getHead(path.getPath() + rev);
				change = (c > change) ? c : change;
			}
			P4Revision revision = new P4Revision(head, new P4ChangeRef(change));
			return revision;
		}
	}
}
