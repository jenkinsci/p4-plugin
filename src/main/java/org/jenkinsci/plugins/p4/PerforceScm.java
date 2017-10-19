package org.jenkinsci.plugins.p4;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.core.Label;
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
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
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
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
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
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.matrix.MatrixOptions;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.p4.tasks.CheckoutTask;
import org.jenkinsci.plugins.p4.tasks.PollTask;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.SpecWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PerforceScm extends SCM {

	private static Logger logger = Logger.getLogger(PerforceScm.class.getName());

	private final String credential;
	private final Workspace workspace;
	private final List<Filter> filter;
	private final Populate populate;
	private final P4Browser browser;

	private transient List<P4Ref> incrementalChanges;
	private transient P4Ref parentChange;
	private transient P4Review review;

	public static final int DEFAULT_FILE_LIMIT = 50;
	public static final int DEFAULT_CHANGE_LIMIT = 20;

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

	public List<P4Ref> getIncrementalChanges() {
		return incrementalChanges;
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
	}

	public PerforceScm(String credential, Workspace workspace, Populate populate) {
		this.credential = credential;
		this.workspace = workspace;
		this.filter = null;
		this.populate = populate;
		this.browser = null;
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
		}
		if (workspace instanceof StreamWorkspaceImpl) {
			StreamWorkspaceImpl ws = (StreamWorkspaceImpl) workspace;
			key.append(ws.getStreamName());
		}
		if (workspace instanceof SpecWorkspaceImpl) {
			SpecWorkspaceImpl ws = (SpecWorkspaceImpl) workspace;
			key.append(ws.getSpecPath());
		}
		if (workspace instanceof StaticWorkspaceImpl) {
			StaticWorkspaceImpl ws = (StaticWorkspaceImpl) workspace;
			key.append(ws.getName());
		}
		if (workspace instanceof TemplateWorkspaceImpl) {
			TemplateWorkspaceImpl ws = (TemplateWorkspaceImpl) workspace;
			key.append(ws.getTemplateName());
		}

		return key.toString();
	}

	public static P4Browser findBrowser(String scmCredential) {
		// Retrieve item from request
		StaplerRequest req = Stapler.getCurrentRequest();
		Job job = req == null ? null : req.findAncestorObject(Job.class);

		// If cannot retrieve item, check from root
		P4BaseCredentials credentials = job == null
				? ConnectionHelper.findCredential(scmCredential, Jenkins.getActiveInstance())
				: ConnectionHelper.findCredential(scmCredential, job);

		if (credentials == null) {
			logger.fine("Could not retrieve credentials from id: '${scmCredential}");
			return null;
		}
		try {
			ConnectionHelper connection = new ConnectionHelper(credentials, null);
			String url = connection.getSwarm();
			if (url != null) {
				return new SwarmBrowser(url);
			} else {
				return null;
			}
		} catch (P4JavaException e) {
			logger.info("Unable to access Perforce Property.");
			return null;
		}
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
		// A baseline is not required... but a baseline object is, so we'll
		// return the NONE object.
		return SCMRevisionState.NONE;
	}

	/**
	 * This method does the actual polling and returns a PollingResult. The
	 * change attribute of the PollingResult the significance of the changes
	 * detected by this poll.
	 */
	@Override
	public PollingResult compareRemoteRevisionWith(Job<?, ?> job, Launcher launcher, FilePath buildWorkspace,
	                                               TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {

		PollingResult state = PollingResult.NO_CHANGES;
		Node node = workspaceToNode(buildWorkspace);

		// Delay polling if build is in progress
		if (job.isBuilding()) {
			listener.getLogger().println("Build in progress, polling delayed.");
			return PollingResult.NO_CHANGES;
		}

		Jenkins j = Jenkins.getInstance();
		if (j == null) {
			listener.getLogger().println("Warning Jenkins instance is null.");
			return PollingResult.NO_CHANGES;
		}

		// Get last run and build workspace
		Run<?, ?> lastRun = job.getLastBuild();
		buildWorkspace = j.getRootPath();

		if (job instanceof MatrixProject) {
			if (isBuildParent(job)) {
				// Poll PARENT only
				EnvVars envVars = job.getEnvironment(node, listener);
				state = pollWorkspace(envVars, listener, buildWorkspace, lastRun);
			} else {
				// Poll CHILDREN only
				MatrixProject matrixProj = (MatrixProject) job;

				Collection<MatrixConfiguration> configs = matrixProj.getActiveConfigurations();

				for (MatrixConfiguration config : configs) {
					EnvVars envVars = config.getEnvironment(node, listener);
					state = pollWorkspace(envVars, listener, buildWorkspace, lastRun);
					// exit early if changes found
					if (state == PollingResult.BUILD_NOW) {
						return PollingResult.BUILD_NOW;
					}
				}
			}
		} else {
			EnvVars envVars = job.getEnvironment(node, listener);
			state = pollWorkspace(envVars, listener, buildWorkspace, lastRun);
		}

		return state;
	}

	/**
	 * Construct workspace from environment and then look for changes.
	 *
	 * @param envVars
	 * @param listener
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private PollingResult pollWorkspace(EnvVars envVars, TaskListener listener, FilePath buildWorkspace, Run<?, ?> lastRun)
			throws InterruptedException, IOException {
		PrintStream log = listener.getLogger();

		// set NODE_NAME to Node or default "master" if not set
		Node node = workspaceToNode(buildWorkspace);
		String nodeName = node.getNodeName();
		nodeName = (nodeName.isEmpty()) ? "master" : nodeName;
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", nodeName));

		Workspace ws = (Workspace) workspace.clone();
		String root = buildWorkspace.getRemote();
		if (root.contains("@")) {
			root = root.replace("@", "%40");
		}
		ws.setRootPath(root);
		ws.setExpand(envVars);

		// don't call setRootPath() here, polling is often on the master

		// Set EXPANDED client
		String client = ws.getFullName();
		String syncID = ws.getSyncID();
		log.println("P4: Polling on: " + nodeName + " with:" + client);

		// Set EXPANDED pinned label/change
		String pin = populate.getPin();
		if (pin != null && !pin.isEmpty()) {
			pin = ws.getExpand().format(pin, false);
			ws.getExpand().set(ReviewProp.LABEL.toString(), pin);
		}

		// Calculate last change, build if null (JENKINS-40356)
		List<P4Ref> lastRefs = TagAction.getLastChange(lastRun, listener, syncID);
		if (lastRefs == null || lastRefs.isEmpty()) {
			return PollingResult.BUILD_NOW;
		}

		// Create task
		PollTask task = new PollTask(filter, lastRefs);
		task.setCredential(credential, lastRun);
		task.setWorkspace(ws);
		task.setListener(listener);
		task.setLimit(pin);

		// Execute remote task
		incrementalChanges = buildWorkspace.act(task);

		// Report changes
		if (!incrementalChanges.isEmpty()) {
			return PollingResult.BUILD_NOW;
		}
		return PollingResult.NO_CHANGES;
	}

	/**
	 * The checkout method is expected to check out modified files into the
	 * project workspace. In Perforce terms a 'p4 sync' on the project's
	 * workspace. Authorisation
	 */
	@Override
	public void checkout(Run<?, ?> run, Launcher launcher, FilePath buildWorkspace, TaskListener listener,
	                     File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {

		if (changelogFile == null) {
			throw new AbortException("Aborting build: changeLogFile not set.");
		}

		PrintStream log = listener.getLogger();
		boolean success = true;

		// Create task
		CheckoutTask task = new CheckoutTask(populate);
		task.setListener(listener);
		task.setCredential(credential, run);

		// Get workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);

		// Add review to environment, if defined
		if (review != null) {
			ws.addEnv(ReviewProp.REVIEW.toString(), review.getId());
			ws.addEnv(ReviewProp.STATUS.toString(), CheckoutStatus.SHELVED.toString());
		}

		// Set the Workspace and initialise
		task.setWorkspace(ws);
		task.initialise();

		// Override build change if polling per change, MUST clear after use.
		if (isIncremental()) {
			task.setIncrementalChanges(incrementalChanges);
		}
		incrementalChanges = new ArrayList<P4Ref>();

		// Add tagging action to build, enabling label support.
		TagAction tag = new TagAction(run, credential);
		tag.setWorkspace(ws);
		tag.setRefChanges(task.getSyncChange());
		// JENKINS-37442: Make the log file name available
		tag.setChangelog(changelogFile.getAbsolutePath());
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

		// Only write change log if build succeeded and changeLogFile has been
		// set.
		if (success) {
			// Calculate changes prior to build (based on last build)
			listener.getLogger().println("P4 Task: saving built changes.");
			List<P4ChangeEntry> changes = calculateChanges(run, task);
			P4ChangeSet.store(changelogFile, changes);
			listener.getLogger().println("... done\n");
		} else {
			String msg = "P4: Build failed";
			logger.warning(msg);
			throw new AbortException(msg);
		}
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

		// Look for all changes since the last build
		Run<?, ?> lastBuild = run.getPreviousBuild();

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

	// Pre Jenkins 2.60
	@Override
	public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
		super.buildEnvVars(build, env);

		TagAction tagAction = TagAction.getLastAction(build);
		P4EnvironmentContributor.buildEnvironment(tagAction, env);
	}

	// Post Jenkins 2.60 JENKINS-37584 JENKINS-40885
	public void buildEnvironment(Run<?, ?> run, Map<String, String> env) {
		TagAction tagAction = TagAction.getLastAction(run);
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

			ConnectionHelper p4 = new ConnectionHelper(run, credential, null);
			String name = build.toString();
			try {
				Label label = p4.getLabel(name);
				String spec = label.getRevisionSpec();
				if (spec != null && !spec.isEmpty()) {
					if (spec.startsWith("@")) {
						spec = spec.substring(1);
					}
					return spec;
				} else {
					// a label, but no RevisionSpec
					return name;
				}
			} catch (Exception e) {
				// not a label
			}

			try {
				String counter = p4.getCounter(name);
				if (!"0".equals(counter)) {
					try {
						// if a change number, add change...
						int change = Integer.parseInt(counter);
						return String.valueOf(change);
					} catch (NumberFormatException n) {
						// no change number in counter
					}
				}
			} catch (Exception e) {
				// not a counter
			}

			p4.disconnect();
			return name;
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

		logger.info("processWorkspaceBeforeDeletion");

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
		} catch (Exception e) {
			logger.warning("P4: Not able to get connection");
			return false;
		}

		// Setup Cleanup Task
		RemoveClientTask task = new RemoveClientTask();
		task.setListener(listener);
		task.setCredential(credential, job);

		// Set workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);
		task.setWorkspace(ws);

		boolean clean = buildWorkspace.act(task);

		logger.info("clean: " + clean);
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

		private boolean deleteClient;
		private boolean deleteFiles;

		private boolean hideTicket;

		private int maxFiles;
		private int maxChanges;

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

		public boolean isDeleteClient() {
			return deleteClient;
		}

		public boolean isDeleteFiles() {
			return deleteFiles;
		}

		public boolean isHideTicket() {
			return hideTicket;
		}

		public int getMaxFiles() {
			return maxFiles;
		}

		public int getMaxChanges() {
			return maxChanges;
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

	/**
	 * Helper: find the Remote/Local Computer used for build
	 *
	 * @param workspace
	 */
	private static Computer workspaceToComputer(FilePath workspace) {
		Jenkins jenkins = Jenkins.getInstance();
		if (workspace != null && workspace.isRemote()) {
			for (Computer computer : jenkins.getComputers()) {
				if (computer.getChannel() == workspace.getChannel()) {
					return computer;
				}
			}
		}
		return null;
	}

	/**
	 * Helper: find the Node for slave build or return current instance.
	 *
	 * @param workspace
	 */
	private static Node workspaceToNode(FilePath workspace) {
		Computer computer = workspaceToComputer(workspace);
		if (computer != null) {
			return computer.getNode();
		}
		Jenkins jenkins = Jenkins.getInstance();
		return jenkins;
	}

	/**
	 * Incremental polling filter is set
	 *
	 * @return true if set
	 */
	private boolean isIncremental() {
		if (filter != null) {
			for (Filter f : filter) {
				if (f instanceof FilterPerChangeImpl) {
					if (((FilterPerChangeImpl) f).isPerChange()) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
