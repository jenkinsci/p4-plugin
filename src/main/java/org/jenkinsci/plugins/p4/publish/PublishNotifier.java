package org.jenkinsci.plugins.p4.publish;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.tasks.PublishTask;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.logging.Logger;

public class PublishNotifier extends Notifier {

	private static Logger logger = Logger.getLogger(PublishNotifier.class.getName());

	private final String credential;
	private final Workspace workspace;
	private final Publish publish;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Publish getPublish() {
		return publish;
	}

	@DataBoundConstructor
	public PublishNotifier(String credential, Workspace workspace, Publish publish) {
		this.credential = credential;
		this.workspace = workspace;
		this.publish = publish;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {

		// return early if publish not required
		if (getPublish().isOnlyOnSuccess() && build.getResult() != Result.SUCCESS) {
			return true;
		}

		FilePath buildWorkspace = build.getWorkspace();
		if (buildWorkspace == null) {
			logger.warning("FilePath is null!");
			return false;
		}

		Workspace ws = getWorkspace().deepClone();

		// Create task
		PublishTask task = new PublishTask(getCredential(), build, listener, getPublish());
		ws = task.setEnvironment(build, ws, buildWorkspace);
		task.setWorkspace(ws);

		// Expand description
		String desc = getPublish().getDescription();
		desc = ws.getExpand().format(desc, false);
		getPublish().setExpandedDesc(desc);

		String publishedChangeId = buildWorkspace.act(task);

		cleanupPerforceClient(build, buildWorkspace, listener);

		return StringUtils.isNotEmpty(publishedChangeId);
	}

	protected void cleanupPerforceClient(Run<?, ?> run, FilePath buildWorkspace, TaskListener listener)
			throws InterruptedException, IOException {
		Workspace ws = getWorkspace().deepClone();

		if (!ws.isCleanup()) {
			logger.info("PublishNotifier: cleanup disabled");
			return;
		}

		logger.info("PublishNotifier: cleanup Client: " + ws.getFullName());

		RemoveClientTask removeClientTask = new RemoveClientTask(getCredential(), run, listener);

		// Override Global settings so that the client is deleted, but the files are preserved.
		removeClientTask.setDeleteClient(true);
		removeClientTask.setDeleteFiles(false);

		// Set workspace used for the Task
		ws = removeClientTask.setEnvironment(run, workspace, buildWorkspace);
		removeClientTask.setWorkspace(ws);

		buildWorkspace.act(removeClientTask);
	}

	public static DescriptorImpl descriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			j.getDescriptorByType(PublishNotifier.DescriptorImpl.class);
		}
		return null;
	}

	@Extension
	@Symbol("publish")
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Perforce: Publish assets";
		}

		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}
	}
}
