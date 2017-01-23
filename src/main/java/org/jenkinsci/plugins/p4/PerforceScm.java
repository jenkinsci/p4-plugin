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
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.RepositoryBrowser;
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
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeParser;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.matrix.MatrixOptions;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.CheckoutTask;
import org.jenkinsci.plugins.p4.tasks.PollTask;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.SpecWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import sun.security.krb5.internal.crypto.Des;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
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

	private transient List<P4Revision> incrementalChanges;
	private transient P4Revision parentChange;

	public String getP4prog() {
		return p4prog;
	}

	public void setP4prog(String p4prog) {
		this.p4prog = p4prog;
	}

	private String p4prog;

	private String p4version;

	/**
	 * JENKINS-37442: We need to store the changelog file name for the build so
	 * that we can expose it to the build environment
	 */
	private transient String changelogFilename = null;

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

	public List<P4Revision> getIncrementalChanges() {
		return incrementalChanges;
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
	                   P4Browser browser, String p4prog, String p4version) {
		this.credential = credential;
		this.workspace = workspace;
		this.filter = filter;
		this.populate = populate;
		this.browser = browser;
		this.p4prog = p4prog;
		this.p4version = p4version;
	}

	public PerforceScm(String credential, Workspace workspace, Populate populate) {
		this.credential = credential;
		this.workspace = workspace;
		this.filter = null;
		this.populate = populate;
		this.browser = null;
		this.p4prog = null;
		this.p4version = null;
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

	@Override
	public RepositoryBrowser<?> guessBrowser() {
		try {
			String scmCredential = getCredential();
			ConnectionHelper connection = new ConnectionHelper(scmCredential, null);
			connection.setP4Prog(getP4progName());
			connection.setP4ProgVersion(getP4ProgVersion());
			String swarm = connection.getSwarm();
			URL url = new URL(swarm);
			return new SwarmBrowser(url);
		} catch (MalformedURLException e) {
			logger.info("Unable to guess repository browser.");
			return null;
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
		P4Revision last = TagAction.getLastChange(lastRun, listener, syncID);
		if (last == null) {
			return PollingResult.BUILD_NOW;
		}

		// Create task
		PollTask task = new PollTask(filter, last);
		task.setCredential(credential);
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

		PrintStream log = listener.getLogger();
		boolean success = true;

		// Create task
		CheckoutTask task = new CheckoutTask(populate);
		task.setListener(listener);
		task.setCredential(credential);

		// Get workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);

		// Set the Workspace and initialise
		task.setWorkspace(ws);
		task.initialise();

		// Override build change if polling per change, MUST clear after use.
		if (isIncremental()) {
			task.setIncrementalChanges(incrementalChanges);
		}
		incrementalChanges = new ArrayList<P4Revision>();

		// Add tagging action to build, enabling label support.
		TagAction tag = new TagAction(run);
		tag.setCredential(credential);
		tag.setWorkspace(ws);
		tag.setBuildChange(task.getSyncChange());
		run.addAction(tag);

		// Invoke build.
		String node = ws.getExpand().get("NODE_NAME");
		Job<?, ?> job = run.getParent();
		if (run instanceof MatrixBuild) {
			parentChange = task.getSyncChange();
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
			if (changelogFile != null) {
				// Calculate changes prior to build (based on last build)
				listener.getLogger().println("P4 Task: saving built changes.");
				List<P4ChangeEntry> changes = calculateChanges(run, task);
				P4ChangeSet.store(changelogFile, changes);
				listener.getLogger().println("... done\n");
				// JENKINS-37442: Make the log file name available
				changelogFilename = changelogFile.getAbsolutePath();
			} else {
				listener.getLogger().println("P4 Task: changeLogFile not set. Not saving built changes.");
			}
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
		Run<?, ?> lastBuild = run.getPreviousSuccessfulBuild();

		String syncID = task.getSyncID();
		P4Revision lastChange = TagAction.getLastChange(lastBuild, task.getListener(), syncID);

		if (lastChange != null) {
			List<P4ChangeEntry> changes;
			changes = task.getChangesFull(lastChange);
			for (P4ChangeEntry c : changes) {
				list.add(c);
			}
		}

		// if empty, look for shelves in current build. The latest change
		// will not get listed as 'p4 changes n,n' will return no change
		if (list.isEmpty()) {
			P4Revision lastRevision = task.getBuildChange();
			if (lastRevision != null) {
				List<P4ChangeEntry> changes;
				changes = task.getChangesFull(lastRevision);
				for (P4ChangeEntry c : changes) {
					list.add(c);
				}
			}
		}

		// still empty! No previous build, so add current
		if ((lastBuild == null) && list.isEmpty()) {
			list.add(task.getCurrentChange());
		}
		return list;
	}

	private String getP4progName() {
		String return_val = "";

		if (p4prog != null && p4prog.length() != 0)
			return_val =  p4prog;
		else if (getDescriptor().p4prog != null && getDescriptor().p4prog.length() != 0)
			return_val = getDescriptor().p4prog;

		if (workspace != null)
			return_val = workspace.getExpand().format(return_val, false);

		return return_val;
	}

	private String getP4ProgVersion() {
		String return_val = "";

		if (this.p4version != null && this.p4version.length() != 0)
			return_val = p4version;
		else if (getDescriptor().p4version != null && getDescriptor().p4version.length() != 0)
			return_val = getDescriptor().p4version;

		if (workspace != null)
			return_val = workspace.getExpand().format(return_val, false);

		return return_val;
	}

	@Override
	public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
		super.buildEnvVars(build, env);

		TagAction tagAction = build.getAction(TagAction.class);
		if (tagAction != null) {
			// Set P4_CHANGELIST value
			if (tagAction.getBuildChange() != null) {
				String change = getChangeNumber(tagAction);
				env.put("P4_CHANGELIST", change);
			}

			// Set P4_CLIENT workspace value
			if (tagAction.getClient() != null) {
				String client = tagAction.getClient();
				env.put("P4_CLIENT", client);
			}

			// Set P4_PORT connection
			if (tagAction.getPort() != null) {
				String port = tagAction.getPort();
				env.put("P4_PORT", port);
			}

			// Set P4_USER connection
			if (tagAction.getUser() != null) {
				String user = tagAction.getUser();
				env.put("P4_USER", user);
			}

			// Set P4_TICKET connection
			Jenkins j = Jenkins.getInstance();
			if (j != null) {
				@SuppressWarnings("unchecked")
				Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
				DescriptorImpl p4scm = (DescriptorImpl) scm;

				if (tagAction.getTicket() != null && !p4scm.isHideTicket()) {
					String ticket = tagAction.getTicket();
					env.put("P4_TICKET", ticket);
				}
			}

			// JENKINS-37442: Make the log file name available
			env.put("HUDSON_CHANGELOG_FILE", StringUtils.defaultIfBlank(changelogFilename, "Not-set"));
		}
	}

	private String getChangeNumber(TagAction tagAction) {
		P4Revision buildChange = tagAction.getBuildChange();

		if (!buildChange.isLabel()) {
			// its a change, so return...
			return buildChange.toString();
		}

		try {
			// it is really a change number, so add change...
			int change = Integer.parseInt(buildChange.toString());
			return String.valueOf(change);
		} catch (NumberFormatException n) {
			// not a change number
		}

		ConnectionHelper p4 = new ConnectionHelper(getCredential(), null);
		p4.setP4Prog(getP4progName());
		p4.setP4ProgVersion(getP4ProgVersion());
		String name = buildChange.toString();
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

	/**
	 * The checkout method should, besides checking out the modified files,
	 * write a changelog.xml file that contains the changes for a certain build.
	 * The changelog.xml file is specific for each SCM implementation, and the
	 * createChangeLogParser returns a parser that can parse the file and return
	 * a ChangeLogSet.
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		return new P4ChangeParser();
	}

	/**
	 * Called before a workspace is deleted on the given node, to provide SCM an
	 * opportunity to perform clean up.
	 */
	@Override
	public boolean processWorkspaceBeforeDeletion(Job<?, ?> job, FilePath buildWorkspace, Node node)
			throws IOException, InterruptedException {

		logger.info("processWorkspaceBeforeDeletion");

		String scmCredential = getCredential();
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
		ConnectionHelper connection = new ConnectionHelper(scmCredential, null);
		connection.setP4Prog(getP4progName());
		connection.setP4ProgVersion(getP4ProgVersion());
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
		RemoveClientTask task = new RemoveClientTask(client);
		task.setListener(listener);
		task.setCredential(credential);

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

		public String getP4prog() {
			return p4prog;
		}

		private String p4prog;

		private String p4version;

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

		public String getP4version() {
			return p4version;
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
				p4prog = json.getString("p4prog");
				p4version = json.getString("p4version");

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
				maxFiles = 50;
				maxChanges = 10;
			}

			save();
			return true;
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 *
		 * @return A list of Perforce credential items to populate the jelly
		 * Select list.
		 */
		public ListBoxModel doFillCredentialItems() {
			return P4CredentialsImpl.doFillCredentialItems();
		}

		public FormValidation doCheckCredential(@QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(value);
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
