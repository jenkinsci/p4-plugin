package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.branch.BranchProjectFactory;
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
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.changes.P4RefBuilder;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.events.P4BranchScanner;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.p4.review.ReviewProp.P4_CHANGE;

public abstract class AbstractP4ScmSource extends SCMSource {

	private static Logger logger = Logger.getLogger(AbstractP4ScmSource.class.getName());

	protected final String credential;

	private List<SCMSourceTrait> traits = new ArrayList<>();

	private String includes;
	private String charset;
	private String format;
	private Populate populate;
	private List<Filter> filter;

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

	@DataBoundSetter
	public void setFilter(List<Filter> filter) {
		this.filter = filter;
	}

	public String getCredential() {
		return credential;
	}

	public List<SCMSourceTrait> getTraits() {
		if (traits == null) {
			traits = new ArrayList<>();
		}
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

	public List<Filter> getFilter() {
		return filter;
	}

	public abstract P4Browser getBrowser();

	public abstract List<P4SCMHead> getHeads(@NonNull TaskListener listener) throws Exception;

	public abstract List<P4SCMHead> getTags(@NonNull TaskListener listener) throws Exception;

	public abstract Workspace getWorkspace(P4Path path);

	public String getScriptPathOrDefault() {
		SCMSourceOwner owner = getOwner();
		if (owner instanceof WorkflowMultiBranchProject) {
			WorkflowMultiBranchProject branchProject = (WorkflowMultiBranchProject) owner;
			BranchProjectFactory<WorkflowJob, WorkflowRun> project = branchProject.getProjectFactory();
			if (project instanceof WorkflowBranchProjectFactory) {
				WorkflowBranchProjectFactory branchProjectFactory = (WorkflowBranchProjectFactory) project;
				return branchProjectFactory.getScriptPath();
			}
		}
		return "Jenkinsfile";
	}

	@Override
	protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {

		try {
			List<P4SCMHead> heads = getHeads(listener);
			List<P4SCMHead> tags = getTags(listener);
			heads.addAll(tags);

			for (P4SCMHead head : heads) {
				logger.fine("SCM: retrieve Head: " + head);

				// get SCMRevision from payload if trigger event, else build from head (latest)
				SCMRevision revision = getRevision(head, listener);
				if (event != null) {
					JSONObject payload = (JSONObject) event.getPayload();
					P4SCMRevision rev = getRevision(payload);
					if (rev.getHead().equals(head)) {
						revision = rev;
						logger.fine("SCM: retrieve (trigger) Revision: " + revision);
					} else {
						if (rev.getHead() instanceof ChangeRequestSCMHead) {
							if (((P4ChangeRequestSCMHead) rev.getHead()).getPath().getPath().equals(head.getPath().getPath())) {
								revision = rev;
								logger.fine("SCM: retrieve (trigger) Swarm Review: " + revision);
							}
						}
					}
				}

				// null criteria means that all branches match.
				if (criteria == null) {
					// get revision and add observe
					observer.observe(head, revision);
				} else {
					try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {
						SCMSourceCriteria.Probe probe = new P4SCMProbe(p4, head);
						if (criteria.isHead(probe, listener)) {
							logger.fine("SCM: observer head: " + head + " revision: " + revision);
							if (revision != null) {
								observer.observe(revision.getHead(), revision);
							}
						}
					}
				}
				// check for user abort
				checkInterrupt();
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	protected SCMProbe createProbe(@NonNull SCMHead head, @CheckForNull SCMRevision revision) throws IOException {
		return newProbe(head, revision);
	}

	@Override
	public PerforceScm build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
		return new P4SCMBuilder(this, head, revision).withTraits(getTraits()).build();
	}

	/**
	 * SCMSource level action. `jenkins.branch.MetadataAction`
	 *
	 * @param event    Optional event (might be null) use payload to help filter calls.
	 * @param listener the listener to report progress on.
	 * @return the list of {@link Action} instances to persist.
	 * @throws IOException          if an error occurs while performing the operation.
	 * @throws InterruptedException if any thread has interrupted the current thread.
	 */
	@Override
	protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event, @NonNull TaskListener listener)
			throws IOException, InterruptedException {
		List<Action> result = new ArrayList<>();

		return result;
	}

	/**
	 * SCMHead level action.
	 *
	 * @param head     Changes on a branch
	 * @param event    Optional event (might be null) use payload to help filter calls.
	 * @param listener the listener to report progress on.
	 * @return the list of {@link Action} instances to persist.
	 * @throws IOException          if an error occurs while performing the operation.
	 * @throws InterruptedException if any thread has interrupted the current thread.
	 */
	@Override
	protected List<Action> retrieveActions(@NonNull SCMHead head, @CheckForNull SCMHeadEvent event, @NonNull TaskListener listener) throws IOException, InterruptedException {
		List<Action> result = new ArrayList<>();

		return result;
	}

	/**
	 * SCMRevision level action.
	 *
	 * @param revision the {@link SCMRevision}
	 * @param event    Optional event (might be null) use payload to help filter calls.
	 * @param listener the listener to report progress on.
	 * @return the list of {@link Action} instances to persist.
	 * @throws IOException          if an error occurs while performing the operation.
	 * @throws InterruptedException if any thread has interrupted the current thread.
	 */
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
	 * @param category the Category
	 * @return {@code true} if the supplied category is enabled for this {@link SCMSource} instance.
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

	/**
	 * Get the Latest change for the path specified in P4SCMHead.
	 *
	 * @param head     SCMHead
	 * @param listener for logging
	 * @return The latest change as a P4SCMRevision object
	 * @throws Exception pushed up stack
	 */
	public P4SCMRevision getRevision(P4SCMHead head, TaskListener listener) throws Exception {

		// TODO look for graph revisions too

		// Check for 'Polling per Change' filter option
		boolean perChange = PerforceScm.isIncremental(getFilter());

		long change;
		P4Path path = head.getPath();

		// Fetch last scan
		P4SCMRevision last = getLastScan(head);

		/* If 'Polling per Change' is disabled then get the latest change; otherwise use the latest change for the
		 * first scan then the oldest un-built change.
		 */
		if (last == null || !perChange) {
			change = findLatestChange(path, listener);
		} else {
			change = findIncrementalChange(path, last.getRef(), listener);
		}

		return new P4SCMRevision(head, new P4ChangeRef(change));
	}

	private long findLatestChange(P4Path path, TaskListener listener) throws Exception {
		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {

			// Changelist 'to' limit (report up to this change)
			long to = getToLimit(p4, path.getRevision());

			// Constrained by headLimit see JENKINS-61745.
			long rangeLimit = to - p4.getHeadLimit();
			String limit = (rangeLimit > 0) ? "@" + rangeLimit + "," : "";

			long change = p4.getHead(path.getPath() + "/..." + limit + "@" + to);

			List<String> maps = path.getMappings();
			if (maps != null && !maps.isEmpty()) {
				for (String map : maps) {
					if (map.startsWith("-")) {
						continue;
					}
					long c = p4.getHead(map + "@" + limit + to);
					change = (c > change) ? c : change;
				}
			}
			return change;
		}
	}

	private long getToLimit(ConnectionHelper p4, String to) throws Exception {
		String counter = p4.getCounter("change");
		long change = Long.parseLong(counter);

		if (to == null) {
			return change;
		}

		try {
			return Long.parseLong(to);
		} catch (NumberFormatException e) {
			return change;
		}
	}

	private long findIncrementalChange(P4Path path, P4Ref ref, TaskListener listener) throws Exception {
		try (ConnectionHelper p4 = new ConnectionHelper(getOwner(), credential, listener)) {

			// Calculate change revision range.
			String to = path.getRevision();
			to = (to != null && !to.isEmpty()) ? to : "now";
			long change = ref.getChange();
			String from = String.valueOf(change + 1);

			long c = p4.getLowestHead(path.getPath() + "/...", from, to);
			change = (c > change) ? c : change;

			List<String> maps = path.getMappings();
			if (maps != null && !maps.isEmpty()) {
				for (String map : maps) {
					c = p4.getLowestHead(map, from, to);
					change = (c > change) ? c : change;
				}
			}
			return change;
		}
	}

	/**
	 * Get the SCMRevision for the last MultiBranch Scan on the specified branch/head
	 *
	 * @param head the branch to scan
	 * @return a P4SCMRevision (change number)
	 */
	private P4SCMRevision getLastScan(P4SCMHead head) {
		SCMSourceOwner owner = getOwner();
		if (!(owner instanceof WorkflowMultiBranchProject)) {
			return null;
		}

		WorkflowMultiBranchProject branchProject = (WorkflowMultiBranchProject) owner;
		BranchProjectFactory<WorkflowJob, WorkflowRun> project = branchProject.getProjectFactory();
		if (project instanceof WorkflowBranchProjectFactory) {
			WorkflowBranchProjectFactory branchProjectFactory = (WorkflowBranchProjectFactory) project;
			WorkflowJob job = branchProject.getJob(head.getName());

			if (job == null) {
				return null;
			}

			SCMRevision r = branchProjectFactory.getRevision(job);
			if (r instanceof P4SCMRevision) {
				return (P4SCMRevision) r;
			}

			return null;
		}
		return null;
	}

	/**
	 * A specific revision based on the Event Payload.
	 *
	 * @param payload JSON payload from an external Event
	 * @return the change as a P4SCMRevision object or null if no match.
	 */
	public P4SCMRevision getRevision(JSONObject payload) {
		// Verify Change is set in JSON
		String change = getProperty(payload, P4_CHANGE);
		if (change == null) {
			return null;
		}

		P4Ref ref = P4RefBuilder.get(change);
		P4BranchScanner scanner = getScanner(ref);
		if (scanner == null) {
			return null;
		}

		String base = scanner.getProjectRoot();
		String branch = scanner.getBranch();
		String path = base + "/" + branch;
		return P4SCMRevision.builder(path, branch, ref);
	}

	/**
	 * Scans for a Jenkinsfile given a submitted change.
	 * <p>
	 * Looks a the first submitted file and walks up the path looking for a Jenkinsfile.
	 *
	 * @param ref A Perforce Change or Label
	 * @return Scanning results.
	 */
	protected P4BranchScanner getScanner(P4Ref ref) {
		P4BaseCredentials baseCredentials = ConnectionHelper.findCredential(credential, getOwner());
		P4BranchScanner scanner = new P4BranchScanner(baseCredentials, ref, getScriptPathOrDefault());

		// Check matching Project path included in Source
		if (scanner.getProjectRoot() == null || !findInclude(scanner.getProjectRoot())) {
			return null;
		}
		return scanner;
	}

	protected boolean findInclude(String path) {
		path = (path.endsWith("/*")) ? path.substring(0, path.lastIndexOf("/*")) : path;
		path = (path.endsWith("/...")) ? path.substring(0, path.lastIndexOf("/...")) : path;

		List<String> includes = getIncludePaths();
		for (String i : includes) {
			if (i.startsWith(path)) {
				return true;
			}
		}
		return false;
	}

	protected String getProperty(JSONObject payload, ReviewProp property) {
		if (!payload.has(property.getProp())) {
			return null;
		}

		String value = payload.getString(property.getProp());
		if (value == null || value.isEmpty()) {
			return null;
		}
		return value;
	}
}
