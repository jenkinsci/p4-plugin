package org.jenkinsci.plugins.p4;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.perforce.p4java.exception.P4JavaException;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Plugin;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixExecutionStrategy;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.build.ExecutorHelper;
import org.jenkinsci.plugins.p4.build.NodeHelper;
import org.jenkinsci.plugins.p4.build.P4EnvironmentContributor;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeParser;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.changes.P4LabelRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.credentials.P4InvalidCredentialException;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterLatestChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.matrix.MatrixOptions;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.AbstractP4ScmSource;
import org.jenkinsci.plugins.p4.scm.P4Path;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.p4.tasks.CheckoutTask;
import org.jenkinsci.plugins.p4.tasks.PollTask;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.tasks.WhereTask;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.SpecWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PerforceScm extends SCM {

	private static Logger logger = Logger.getLogger(PerforceScm.class.getName());

	private final String credential;
	private final Workspace workspace;
	private final List<Filter> filter;
	private final Populate populate;
	private final P4Browser browser;

	private P4Ref revision;

	private String script;

	private transient TagAction tagAction = null;
	private transient P4Ref parentChange;
	private transient P4Review review;

	public static final int DEFAULT_FILE_LIMIT = 50;
	public static final int DEFAULT_CHANGE_LIMIT = 20;
	public static final long DEFAULT_HEAD_LIMIT = 1000;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public List<Filter> getFilter() {
		return filter;
	}

	public Populate getPopulate() {
		return populate;
	}

	@Override
	public P4Browser getBrowser() {
		return browser;
	}

	public P4Review getReview() {
		return review;
	}

	public void setReview(P4Review review) {
		this.review = review;
	}

	/**
	 * Helper function for converting an SCM object into a
	 * PerforceScm object when appropriate.
	 *
	 * @param scm the SCM object
	 * @return a PerforceScm instance or null
	 */
	public static PerforceScm convertToPerforceScm(SCM scm) {
		PerforceScm perforceScm = null;
		if (scm instanceof PerforceScm) {
			perforceScm = (PerforceScm) scm;
		} else {
			Jenkins jenkins = Jenkins.getInstance();
			if (jenkins != null) {
				Plugin multiSCMPlugin = jenkins.getPlugin("multiple-scms");
				if (multiSCMPlugin != null) {
					if (scm instanceof MultiSCM) {
						MultiSCM multiSCM = (MultiSCM) scm;
						for (SCM configuredSCM : multiSCM.getConfiguredSCMs()) {
							if (configuredSCM instanceof PerforceScm) {
								perforceScm = (PerforceScm) configuredSCM;
								break;
							}
						}
					}
				}
			}
		}
		return perforceScm;
	}

	/**
	 * Create a constructor that takes non-transient fields, and add the
	 * annotation @DataBoundConstructor to it. Using the annotation helps the
	 * Stapler class to find which constructor that should be used when
	 * automatically copying values from a web form to a class.
	 *
	 * @param credential Credential ID
	 * @param workspace  Workspace connection details
	 * @param filter     Polling filters
	 * @param populate   Populate options
	 * @param browser    Browser options
	 */
	@DataBoundConstructor
	public PerforceScm(String credential, Workspace workspace, List<Filter> filter, Populate populate,
					   P4Browser browser) {
		this.credential = credential;
		this.workspace = workspace;
		this.filter = filter;
		this.populate = populate;
		this.browser = browser;
		this.revision = null;
	}

	/**
	 * MultiBranch constructor for building jobs.
	 *
	 * @param source   ScmSource
	 * @param path     Perforce project path and mappings
	 * @param revision Perforce revision
	 */
	public PerforceScm(AbstractP4ScmSource source, P4Path path, P4Ref revision) {
		this.credential = source.getCredential();
		this.workspace = source.getWorkspace(path);
		this.filter = source.getFilter();
		this.populate = source.getPopulate();
		this.browser = source.getBrowser();
		this.revision = revision;
		this.script = source.getScriptPathOrDefault();
	}

	/**
	 * Internal constructor for functional tests.
	 *
	 * @param credential Credential ID
	 * @param workspace  Workspace type
	 * @param populate   Populate options
	 */
	public PerforceScm(String credential, Workspace workspace, Populate populate) {
		this.credential = credential;
		this.workspace = workspace;
		this.filter = null;
		this.populate = populate;
		this.browser = null;
		this.revision = null;
	}

	@Override
	public String getKey() {
		String delim = "-";
		StringBuffer key = new StringBuffer("p4");

		// add Credential
		key.append(delim);
		key.append(credential);

		// add Mapping/Stream
		key.append(delim);
		if (workspace instanceof ManualWorkspaceImpl) {
			ManualWorkspaceImpl ws = (ManualWorkspaceImpl) workspace;
			key.append(ws.getSpec().getView());
			key.append(ws.getSpec().getStreamName());
			key.append(ws.getName());
		}
		if (workspace instanceof StreamWorkspaceImpl) {
			StreamWorkspaceImpl ws = (StreamWorkspaceImpl) workspace;
			key.append(ws.getStreamName());
			key.append(ws.getStreamAtChange());
			key.append(ws.getName());
		}
		if (workspace instanceof SpecWorkspaceImpl) {
			SpecWorkspaceImpl ws = (SpecWorkspaceImpl) workspace;
			key.append(ws.getSpecPath());
			key.append(ws.getName());
		}
		if (workspace instanceof StaticWorkspaceImpl) {
			StaticWorkspaceImpl ws = (StaticWorkspaceImpl) workspace;
			key.append(ws.getName());
		}
		if (workspace instanceof TemplateWorkspaceImpl) {
			TemplateWorkspaceImpl ws = (TemplateWorkspaceImpl) workspace;
			key.append(ws.getTemplateName());
			key.append(ws.getName());
		}

		return key.toString();
	}

	public static P4Browser findBrowser(String scmCredential) {
		// Retrieve item from request
		StaplerRequest req = Stapler.getCurrentRequest();
		Job job = req == null ? null : req.findAncestorObject(Job.class);

		// If cannot retrieve item, check from root
		P4BaseCredentials credentials = job == null
				? ConnectionHelper.findCredential(scmCredential, Jenkins.getInstance())
				: ConnectionHelper.findCredential(scmCredential, job);

		if (credentials == null) {
			logger.fine("Could not retrieve credentials from id: '${scmCredential}");
			return null;
		}
		try (ConnectionHelper p4 = new ConnectionHelper(credentials, null)) {
			String url = p4.getSwarm();
			if (url != null) {
				return new SwarmBrowser(url);
			} else {
				return null;
			}
		} catch (IOException e) {
			logger.severe("Connection error. " + e.getMessage());
		} catch (P4JavaException e) {
			logger.severe("Unable to access Swarm Property. " + e.getMessage());
		} catch (Exception e) {
			logger.severe("Perforce resource error. " + e.getMessage());
		}

		return null;
	}

	/**
	 * Calculate the state of the workspace of the given build. The returned
	 * object is then fed into compareRemoteRevisionWith as the baseline
	 * SCMRevisionState to determine if the build is necessary, and is added to
	 * the build as an Action for later retrieval.
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> run, FilePath buildWorkspace, Launcher launcher,
												   TaskListener listener) throws IOException, InterruptedException {
		// return the Perforce change; this gets updated during polling...
		return new PerforceRevisionState(new P4LabelRef("now"));
	}

	/**
	 * This method does the actual polling and returns a PollingResult. The
	 * change attribute of the PollingResult the significance of the changes
	 * detected by this poll.
	 */
	@Override
	public PollingResult compareRemoteRevisionWith(Job<?, ?> job, Launcher launcher, FilePath buildWorkspace,
												   TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {

		String jobName = job.getName();
		logger.finer("P4: polling[" + jobName + "] started...");

		PollingResult state = PollingResult.NO_CHANGES;

		// Get last run and build workspace
		Run<?, ?> lastRun = job.getLastBuild();

		Queue.Item[] items = Jenkins.getInstance().getQueue().getItems();
		for (Queue.Item item : items) {
			if (item.task instanceof WorkflowJob) {
				WorkflowJob task = (WorkflowJob) item.task;
				if (task.equals(job)) {
					if (item instanceof Queue.WaitingItem) {
						logger.info("P4: polling[" + jobName + "] skipping WaitingItem");
						return PollingResult.NO_CHANGES;
					}
					if (item instanceof Queue.BlockedItem) {
						logger.finer("P4: polling[" + jobName + "] BlockedItem");
					}
					if (item instanceof Queue.BuildableItem) {
						logger.finer("P4: polling[" + jobName + "] BuildableItem");
					}
				}
			}

		}

		// Build workspace is often null as requiresWorkspaceForPolling() returns false as a checked out workspace is
		// not needed, but we still need a client and artificial root for the view.
		// JENKINS-46908
		if (buildWorkspace == null) {
			String defaultRoot = job.getRootDir().getAbsoluteFile().getAbsolutePath();
			if (lastRun != null) {
				EnvVars env = lastRun.getEnvironment(listener);
				buildWorkspace = new FilePath(new File(env.get("WORKSPACE", defaultRoot)));
			} else {
				listener.getLogger().println("Warning Jenkins Workspace root not defined.");
				return PollingResult.NO_CHANGES;
			}
		}

		Node node = NodeHelper.workspaceToNode(buildWorkspace);

		if (job instanceof MatrixProject) {
			if (isBuildParent(job)) {
				// Poll PARENT only
				EnvVars envVars = job.getEnvironment(node, listener);
				state = pollWorkspace(envVars, listener, buildWorkspace, lastRun, baseline);
			} else {
				// Poll CHILDREN only
				MatrixProject matrixProj = (MatrixProject) job;

				Collection<MatrixConfiguration> configs = matrixProj.getActiveConfigurations();

				for (MatrixConfiguration config : configs) {
					EnvVars envVars = config.getEnvironment(node, listener);
					state = pollWorkspace(envVars, listener, buildWorkspace, lastRun, baseline);
					// exit early if changes found
					if (state == PollingResult.BUILD_NOW) {
						logger.finer("P4: polling[" + jobName + "] exit (Matrix): " + state.change);
						return state;
					}
				}
			}
		} else {
			EnvVars envVars = job.getEnvironment(node, listener);
			state = pollWorkspace(envVars, listener, buildWorkspace, lastRun, baseline);
		}

		logger.finer("P4: polling[" + jobName + "] exit: " + state.change);
		return state;
	}

	private boolean isConcurrentBuild(Job<?, ?> job) {
		return job instanceof AbstractProject ? ((AbstractProject) job).isConcurrentBuild() :
				job.getProperty(DisableConcurrentBuildsJobProperty.class) == null;
	}

	/**
	 * Construct workspace from environment and then look for changes.
	 *
	 * @param envVars
	 * @param listener
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private PollingResult pollWorkspace(EnvVars envVars, TaskListener listener, FilePath buildWorkspace, Run<?, ?> lastRun, SCMRevisionState baseline)
			throws InterruptedException, IOException {

		// set NODE_NAME to Node or default "master" if not set
		String nodeName = NodeHelper.getNodeName(buildWorkspace);
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", nodeName));
		String executor = ExecutorHelper.getExecutorID(buildWorkspace, listener);
		envVars.put("EXECUTOR_NUMBER", envVars.get("EXECUTOR_NUMBER", executor));

		Workspace ws = workspace.deepClone();

		// JENKINS-48434 by setting rootPath to null will leave client's root unchanged
		ws.setRootPath(null);
		ws.setExpand(envVars);

		// Set EXPANDED client
		String client = ws.getFullName();
		listener.getLogger().println("P4: Polling on: " + nodeName + " with:" + client);

		List<P4Ref> changes = lookForChanges(buildWorkspace, ws, lastRun, listener);

		// Cleanup Perforce Client
		cleanupPerforceClient(lastRun, buildWorkspace, listener, ws);

		// Build if changes are found or report NO_CHANGE
		if (changes == null) {
			listener.getLogger().println("P4: Polling error; no previous change.");
			return PollingResult.NO_CHANGES;
		} else if (changes.isEmpty()) {
			listener.getLogger().println("P4: Polling no changes found.");
			return PollingResult.NO_CHANGES;
		} else {
			changes.forEach((c) -> listener.getLogger().println("P4: Polling found change: " + c));
			if (baseline instanceof PerforceRevisionState) {
				PerforceRevisionState p4Baseline = (PerforceRevisionState) baseline;
				setLatestChange(changes, listener, p4Baseline);
			}
			return PollingResult.BUILD_NOW;
		}
	}

	/**
	 * Update the baseline with the latest polled change.
	 *
	 * @param changes  changes found during poll
	 * @param listener for logging
	 * @param baseline the baseline storing the Perforce change
	 */
	private void setLatestChange(List<P4Ref> changes, TaskListener listener, PerforceRevisionState baseline) {
		long change = 0L;
		for (P4Ref c : changes) {
			change = Math.max(change, c.getChange());
		}
		if (change > 0L && FilterLatestChangeImpl.isActive(getFilter())) {
			listener.getLogger().println("P4: Setting Latest change to: " + change);
			baseline.setChange(new P4ChangeRef(change));
		}
	}

	/**
	 * Look for un-built for changes
	 *
	 * @param ws
	 * @param lastRun
	 * @param listener
	 * @return A list of changes found during polling, empty list if no changes (up-to-date).
	 * Will return 'null' if no previous build.
	 */
	private List<P4Ref> lookForChanges(FilePath buildWorkspace, Workspace ws, Run<?, ?> lastRun, TaskListener listener)
			throws IOException, InterruptedException {

		// Set EXPANDED client
		String syncID = ws.getSyncID();

		// Set EXPANDED pinned label/change
		String pin = populate.getPin();
		if (pin != null && !pin.isEmpty()) {
			pin = ws.getExpand().format(pin, false);
			ws.getExpand().set(ReviewProp.P4_LABEL.toString(), pin);
		}

		// Calculate last change, build if null (JENKINS-40356)
		List<P4Ref> lastRefs = TagAction.getLastChange(lastRun, listener, syncID);
		if (lastRefs == null || lastRefs.isEmpty()) {
			// no previous build, return null.
			listener.getLogger().println("P4: Polling: No changes in previous build.");
			return null;
		}

		// Create task
		PollTask task = new PollTask(credential, lastRun, listener, filter, lastRefs);
		task.setWorkspace(ws);
		task.setLimit(pin);

		// Execute remote task
		List<P4Ref> changes = buildWorkspace.act(task);
		return changes;
	}

	/**
	 * The checkout method is expected to check out modified files into the
	 * project workspace. In Perforce terms a 'p4 sync' on the project's
	 * workspace. Authorisation
	 */
	@Override
	public void checkout(Run<?, ?> run, Launcher launcher, FilePath buildWorkspace, TaskListener listener,
						 File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {

		String jobName = run.getParent().getName();
		logger.finer("P4: checkout[" + jobName + "] started...");

		PrintStream log = listener.getLogger();
		boolean success = true;

		CheckoutTask task = null;

		if (run instanceof FreeStyleBuild && populate instanceof SyncOnlyImpl) {
			String p4CleanWorkspace;
			EnvVars envVars = run.getEnvironment(listener);
			p4CleanWorkspace = envVars.getOrDefault("P4_CLEANWORKSPACE", "false");

			if (p4CleanWorkspace.equals("true")) {
				listener.getLogger().println("P4: Wiping workspace...");
				buildWorkspace.deleteRecursive();

				Populate populateWithForceSync = new SyncOnlyImpl(
						((SyncOnlyImpl) populate).isRevert(),
						true,
						true,
						populate.isModtime(),
						populate.isQuiet(),
						populate.getPin(),
						populate.getParallel());
				task = new CheckoutTask(credential, run, listener, populateWithForceSync);
			}
		}

		// Create task
		if (task == null) {
			task = new CheckoutTask(credential, run, listener, populate);
		}

		// Update credential tracking
		try {
			CredentialsProvider.track(run, task.getCredential());
		} catch (P4InvalidCredentialException e) {
			String err = "P4: Unable to checkout: " + e;
			logger.severe(err);
			throw new AbortException(err);
		}

		// Get workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);

		// Add review to environment, if defined
		if (review != null) {
			ws.addEnv(ReviewProp.SWARM_REVIEW.toString(), review.getId());
			ws.addEnv(ReviewProp.SWARM_STATUS.toString(), CheckoutStatus.SHELVED.toString());
		}

		// Set the Workspace and initialise
		task.setWorkspace(ws);
		task.initialise();

		// Override build change if polling per change.
		if (FilterPerChangeImpl.isActive(getFilter())) {
			String p4OneChangelist = null;

			if (run instanceof FreeStyleBuild) {
				EnvVars envVars = run.getEnvironment(listener);
				p4OneChangelist = envVars.get("P4_INCREMENTAL");
			}

			if (p4OneChangelist == null || p4OneChangelist.equals("true")) {
				Run<?, ?> lastRun = run.getPreviousBuiltBuild();
				/* Fix for JENKINS-58639
				Check if a previous build is in progress. If yes, do not try and build the same change that is being built.
				To help lookForChanges() find the correct change to build, sending it the previousBuildInProgress
				if not null else previousBuiltBuild.
				 */
				Run<?, ?> inProgressRun = run.getPreviousBuildInProgress();
				if (inProgressRun != null) {
					lastRun = inProgressRun;
				}
				List<P4Ref> changes = lookForChanges(buildWorkspace, ws, lastRun, listener);
				task.setIncrementalChanges(changes);
			}
		}

		// If the Latest Change filter is set, apply the baseline change to the checkout task.
		if (FilterLatestChangeImpl.isActive(getFilter())) {
			if (baseline instanceof PerforceRevisionState) {
				PerforceRevisionState p4baseline = (PerforceRevisionState) baseline;
				log.println("Baseline: " + p4baseline.getChange().toString());
				task.setBuildChange(p4baseline.getChange());
			}
		}

		// SCMRevision build per change
		if (revision != null) {
			List<P4Ref> changes = Arrays.asList(revision);
			task.setIncrementalChanges(changes);
		}

		// Add tagging action to build, enabling label support.
		TagAction tag = new TagAction(run, credential);
		tag.setWorkspace(ws);
		tag.setRefChanges(task.getSyncChange());
		// JENKINS-37442: Make the log file name available
		tag.setChangelog(changelogFile);
		// JENKINS-39107: Make Depot location of Jenkins file available
		String jenkinsPath;
		Optional<TagAction> actions = getTagActionWithNullWorkspace(run);
		Optional<TagAction> previousAction = getActionWithJenkinsFilePath(run);
		if (actions.isPresent()) {
			jenkinsPath = actions.get().getJenkinsPath();
			run.removeAction(actions.get());
		} else if (previousAction.isPresent()) {
			jenkinsPath = previousAction.get().getJenkinsPath();
		} else {
			WhereTask where = new WhereTask(credential, run, listener, getScriptPath(run));
			where.setWorkspace(ws);
			jenkinsPath = buildWorkspace.act(where);
		}
		tag.setJenkinsPath(jenkinsPath);
		tagAction = tag;
		run.addAction(tag);

		// Invoke build.
		String node = ws.getExpand().get("NODE_NAME");
		Job<?, ?> job = run.getParent();
		if (run instanceof MatrixBuild) {
			parentChange = new P4LabelRef(getChangeNumber(tag, run));
			if (isBuildParent(job)) {
				log.println("Building Parent on Node: " + node);
				success &= buildWorkspace.act(task);
			} else {
				listener.getLogger().println("Skipping Parent build...");
				success = true;
			}
		} else {
			if (job instanceof MatrixProject) {
				if (parentChange != null) {
					log.println("Using parent change: " + parentChange);
					task.setBuildChange(parentChange);
				}
				log.println("Building Child on Node: " + node);
			} else {
				log.println("Building on Node: " + node);
			}
			success &= buildWorkspace.act(task);
		}

		// Abort if build failed
		if (!success) {
			String msg = "P4: Build failed";
			logger.warning(msg);
			throw new AbortException(msg);
		}

		// Write change log if changeLogFile has been set.
		if (changelogFile != null) {
			// Calculate changes prior to build (based on last build)
			listener.getLogger().println("P4: saving built changes.");
			List<P4ChangeEntry> changes = calculateChanges(run, task);
			P4ChangeSet.store(changelogFile, changes);
			listener.getLogger().println("... done\n");
		} else {
			logger.fine("P4: unable to save changes, null changelogFile.");
		}

		// Cleanup Perforce Client
		cleanupPerforceClient(run, buildWorkspace, listener, ws);

		logger.finer("P4: checkout[" + jobName + "] finished.");
	}

	private static Optional<TagAction> getActionWithJenkinsFilePath(Run<?, ?> run) {
		return run.getActions(TagAction.class).stream()
				.filter(action -> StringUtils.isNotEmpty(action.getJenkinsPath()))
				.findAny();
	}

	private static Optional<TagAction> getTagActionWithNullWorkspace(Run<?, ?> run) {
		return run.getActions(TagAction.class).stream()
				.filter(action -> Objects.isNull(action.getWorkspace()))
				.findAny();
	}

	private String getScriptPath(Run<?, ?> run) {
		if (script != null) {
			return script;
		}

		if (!(run instanceof WorkflowRun)) {
			return null;
		}

		WorkflowRun workflowRun = (WorkflowRun) run;
		WorkflowJob job = workflowRun.getParent();
		return getScriptPath(job);
	}

	public String getScriptPath(Item item) {
		if (!(item instanceof WorkflowJob)) {
			return null;
		}

		WorkflowJob job = (WorkflowJob) item;
		FlowDefinition definition = job.getDefinition();
		if (!(definition instanceof CpsScmFlowDefinition)) {
			return null;
		}

		CpsScmFlowDefinition cps = (CpsScmFlowDefinition) definition;
		if (!(cps.getScm() instanceof PerforceScm)) {
			return null;
		}

		return cps.getScriptPath();
	}

	private void cleanupPerforceClient(Run<?, ?> run, FilePath buildWorkspace, TaskListener listener, Workspace workspace) throws IOException, InterruptedException {
		// exit early if cleanup not required
		if (!workspace.isCleanup()) {
			return;
		}

		listener.getLogger().println("P4Task: cleanup Client: " + workspace.getFullName());
		RemoveClientTask removeClientTask = new RemoveClientTask(credential, run, listener);

		// Override Global settings so that the client is deleted, but the files are preserved.
		removeClientTask.setDeleteClient(true);
		removeClientTask.setDeleteFiles(false);

		// Set workspace used for the Task
		Workspace ws = removeClientTask.setEnvironment(run, workspace, buildWorkspace);
		removeClientTask.setWorkspace(ws);

		buildWorkspace.act(removeClientTask);
	}

	// Get Matrix Execution options
	private MatrixExecutionStrategy getMatrixExecutionStrategy(Job<?, ?> job) {
		if (job instanceof MatrixProject) {
			MatrixProject matrixProj = (MatrixProject) job;
			return matrixProj.getExecutionStrategy();
		}
		return null;
	}

	boolean isBuildParent(Job<?, ?> job) {
		MatrixExecutionStrategy matrix = getMatrixExecutionStrategy(job);
		if (matrix instanceof MatrixOptions) {
			return ((MatrixOptions) matrix).isBuildParent();
		} else {
			// if user hasn't configured "Perforce: Matrix Options" execution
			// strategy, default to false
			return false;
		}
	}

	private List<P4ChangeEntry> calculateChanges(Run<?, ?> run, CheckoutTask task) {
		List<P4ChangeEntry> list = new ArrayList<P4ChangeEntry>();

		Run<?, ?> lastBuild;
		PerforceScm.DescriptorImpl scm = getDescriptor();
		if (scm != null && scm.isLastSuccess()) {
			// JENKINS-64030 Include changes since last successful build
			lastBuild = run.getPreviousSuccessfulBuild();
		} else {
			// JENKINS-40747 Look for all changes since the last (completed) build.
			// The lastBuild from getPreviousBuild() may be in progress or blocked.
			lastBuild = run.getPreviousCompletedBuild();
		}

		String syncID = task.getSyncID();
		List<P4Ref> lastRefs = TagAction.getLastChange(lastBuild, task.getListener(), syncID);

		if (lastRefs != null && !lastRefs.isEmpty()) {
			list.addAll(task.getChangesFull(lastRefs));
		}

		// if empty, look for shelves in current build. The latest change
		// will not get listed as 'p4 changes n,n' will return no change
		if (list.isEmpty()) {
			List<P4Ref> lastRevisions = new ArrayList<>();
			lastRevisions.add(task.getBuildChange());
			if (lastRevisions != null && !lastRevisions.isEmpty()) {
				list.addAll(task.getChangesFull(lastRevisions));
			}
		}

		// still empty! No previous build, so add current
		if ((lastBuild == null) && list.isEmpty()) {
			list.add(task.getCurrentChange());
		}

		return list;
	}

	// Post Jenkins 2.60 JENKINS-37584 JENKINS-40885 JENKINS-52806 JENKINS-60074
	public void buildEnvironment(Run<?, ?> run, Map<String, String> env) {
		P4EnvironmentContributor.buildEnvironment(TagAction.getLastAction(run), env);
		P4EnvironmentContributor.buildEnvironment(tagAction, env);
	}

	private String getChangeNumber(TagAction tagAction, Run<?, ?> run) {
		List<P4Ref> builds = tagAction.getRefChanges();

		for (P4Ref build : builds) {
			if (build instanceof P4ChangeRef) {
				// its a change, so return...
				return build.toString();
			}

			if (build instanceof P4GraphRef) {
				continue;
			}

			try {
				// it is really a change number, so add change...
				int change = Integer.parseInt(build.toString());
				return String.valueOf(change);
			} catch (NumberFormatException n) {
				// not a change number
			}

			try (ConnectionHelper p4 = new ConnectionHelper(run, credential, null)) {
				String name = build.toString();

				// look for a label and return a change
				String change = p4.labelToChange(name);
				if (change != null) {
					return change;
				}

				// look for a counter and return change
				change = p4.counterToChange(name);
				if (change != null) {
					return change;
				}
			} catch (Exception e) {
				// log error, but try next item
				logger.severe("P4: Unable to getChangeNumber: " + e.getMessage());
			}
		}
		return "";
	}

	/**
	 * The checkout method should, besides checking out the modified files,
	 * write a changelog.xml file that contains the changes for a certain build.
	 * The changelog.xml file is specific for each SCM implementation, and the
	 * createChangeLogParser returns a parser that can parse the file and return
	 * a ChangeLogSet.
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		return new P4ChangeParser(credential);
	}

	/**
	 * Called before a workspace is deleted on the given node, to provide SCM an
	 * opportunity to perform clean up.
	 */
	@Override
	public boolean processWorkspaceBeforeDeletion(Job<?, ?> job, FilePath buildWorkspace, Node node)
			throws IOException, InterruptedException {

		logger.finer("processWorkspaceBeforeDeletion");

		Run<?, ?> run = job.getLastBuild();

		if (run == null) {
			logger.warning("P4: No previous builds found");
			return false;
		}

		// exit early if client workspace is undefined
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		EnvVars envVars = run.getEnvironment(listener);
		String client = envVars.get("P4_CLIENT");
		if (client == null || client.isEmpty()) {
			logger.warning("P4: Unable to read P4_CLIENT");
			return false;
		}

		// exit early if client workspace does not exist
		ConnectionHelper connection = new ConnectionHelper(run, credential, null);
		try {
			if (!connection.isClient(client)) {
				logger.warning("P4: client not found:" + client);
				return false;
			}
			//JENKINS-60144 Checking if the template ws exists before deleting client, otherwise it throws exception along the line.
			if (workspace instanceof TemplateWorkspaceImpl) {
				TemplateWorkspaceImpl template = (TemplateWorkspaceImpl) workspace;
				boolean exists = template.templateExists(connection.getConnection());
				if (!exists) {
					return false;
				}
			}
		} catch (Exception e) {
			logger.warning("P4: Not able to get connection");
			return false;
		}

		// Setup Cleanup Task
		RemoveClientTask task = new RemoveClientTask(credential, job, listener);

		// Set workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);
		task.setWorkspace(ws);

		boolean clean = buildWorkspace.act(task);

		logger.finer("processWorkspaceBeforeDeletion cleaned: " + clean);
		return clean;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * The relationship of Descriptor and SCM (the describable) is akin to class
	 * and object. What this means is that the descriptor is used to create
	 * instances of the describable. Usually the Descriptor is an internal class
	 * in the SCM class named DescriptorImpl. The Descriptor should also contain
	 * the global configuration options as fields, just like the SCM class
	 * contains the configurations options for a job.
	 *
	 * @author pallen
	 */
	@Extension
	@Symbol("perforce")
	public static class DescriptorImpl extends SCMDescriptor<PerforceScm> {

		private boolean autoSave;
		private String credential;
		private String clientName;
		private String depotPath;
		private boolean autoSubmitOnChange;

		private boolean deleteClient;
		private boolean deleteFiles;

		private boolean hideTicket;
		private boolean hideMessages;

		private int maxFiles = DEFAULT_FILE_LIMIT;
		private int maxChanges = DEFAULT_CHANGE_LIMIT;

		private long headLimit = DEFAULT_HEAD_LIMIT;

		private boolean lastSuccess;

		public boolean isAutoSave() {
			return autoSave;
		}

		public String getCredential() {
			return credential;
		}

		public String getClientName() {
			return clientName;
		}

		public String getDepotPath() {
			return depotPath;
		}

		public boolean isAutoSubmitOnChange() {
			return autoSubmitOnChange;
		}

		public boolean isDeleteClient() {
			return deleteClient;
		}

		public boolean isDeleteFiles() {
			return deleteFiles;
		}

		public boolean isHideTicket() {
			return hideTicket;
		}

		public boolean isHideMessages() {
			return hideMessages;
		}

		public int getMaxFiles() {
			return maxFiles;
		}

		public int getMaxChanges() {
			return maxChanges;
		}

		public long getHeadLimit() {
			return headLimit;
		}

		public boolean isLastSuccess() {
			return lastSuccess;
		}

		public void setLastSuccess(boolean lastSuccess) {
			this.lastSuccess = lastSuccess;
		}

		/**
		 * public no-argument constructor
		 */
		public DescriptorImpl() {
			super(PerforceScm.class, P4Browser.class);
			load();
		}

		/**
		 * Returns the name of the SCM, this is the name that will show up next
		 * to CVS and Subversion when configuring a job.
		 */
		@Override
		public String getDisplayName() {
			return "Perforce Software";
		}

		@Override
		public boolean isApplicable(Job project) {
			return true;
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			PerforceScm scm = (PerforceScm) super.newInstance(req, formData);
			return scm;
		}

		/**
		 * The configure method is invoked when the global configuration page is
		 * submitted. In the method the data in the web form should be copied to
		 * the Descriptor's fields. To persist the fields to the global
		 * configuration XML file, the save() method must be called. Data is
		 * defined in the global.jelly page.
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

			try {
				autoSave = json.getBoolean("autoSave");
				credential = json.getString("credential");
				clientName = json.getString("clientName");
				depotPath = json.getString("depotPath");
				autoSubmitOnChange = json.getBoolean("autoSubmitOnChange");
			} catch (JSONException e) {
				logger.info("Unable to read Auto Version configuration.");
				autoSave = false;
			}

			try {
				deleteClient = json.getBoolean("deleteClient");
				deleteFiles = json.getBoolean("deleteFiles");
			} catch (JSONException e) {
				logger.info("Unable to read client cleanup configuration.");
				deleteClient = false;
				deleteFiles = false;
			}

			try {
				hideTicket = json.getBoolean("hideTicket");
			} catch (JSONException e) {
				logger.info("Unable to read TICKET security configuration.");
				hideTicket = false;
			}

			try {
				maxFiles = json.getInt("maxFiles");
				maxChanges = json.getInt("maxChanges");
			} catch (JSONException e) {
				logger.info("Unable to read Max limits in configuration.");
				maxFiles = DEFAULT_FILE_LIMIT;
				maxChanges = DEFAULT_CHANGE_LIMIT;
			}

			try {
				headLimit = json.getLong("headLimit");
			} catch (JSONException e) {
				logger.info("Unable to read query limits in configuration.");
				headLimit = DEFAULT_HEAD_LIMIT;
			}

			try {
				lastSuccess = json.getBoolean("lastSuccess");
				hideMessages = json.getBoolean("hideMessages");
			} catch (JSONException e) {
				logger.info("Unable to read Reporting options in configuration");
				lastSuccess = false;
				hideMessages = false;
			}

			save();
			return true;
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 *
		 * @param project    Jenkins project item
		 * @param credential Perforce credential ID
		 * @return A list of Perforce credential items to populate the jelly
		 * Select list.
		 */
		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 *
		 * @param project Jenkins project item
		 * @param value   credential user input value
		 * @return A list of Perforce credential items to populate the jelly
		 * Select list.
		 */
		public FormValidation doCheckCredential(@AncestorInPath Item project, @QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(project, value);
		}
	}

	/**
	 * This methods determines if the SCM plugin can be used for polling
	 */
	@Override
	public boolean supportsPolling() {
		return true;
	}

	/**
	 * This method should return true if the SCM requires a workspace for
	 * polling. Perforce however can report submitted, pending and shelved
	 * changes without needing a workspace
	 */
	@Override
	public boolean requiresWorkspaceForPolling() {
		return false;
	}
}
