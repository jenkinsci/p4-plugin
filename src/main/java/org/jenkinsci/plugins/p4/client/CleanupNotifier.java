package org.jenkinsci.plugins.p4.client;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.RemoveClientTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

public class CleanupNotifier extends Notifier implements SimpleBuildStep {

	protected static final Logger logger = Logger.getLogger(CleanupNotifier.class.getName());

	public final boolean deleteClient;

	@DataBoundConstructor
	public CleanupNotifier(boolean deleteClient) {
		this.deleteClient = deleteClient;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Extension
	@Symbol({"cleanup", "p4cleanup"})
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce: Cleanup";
		}
	}

	@Override
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath buildWorkspace, @NonNull Launcher launcher, @NonNull TaskListener listener)
			throws InterruptedException, IOException {

		TagAction tagAction = TagAction.getLastAction(run);

		String credential = tagAction.getCredential();
		Workspace workspace = tagAction.getWorkspace();

		// Setup Cleanup Task
		RemoveClientTask task = new RemoveClientTask(credential, run, listener);
		task.setDeleteClient(deleteClient);

		// Set workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);
		task.setWorkspace(ws);

		buildWorkspace.act(task);
	}
}
