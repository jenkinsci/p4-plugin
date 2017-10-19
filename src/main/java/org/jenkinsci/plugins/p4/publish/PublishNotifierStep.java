package org.jenkinsci.plugins.p4.publish;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.tasks.PublishTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class PublishNotifierStep extends PublishNotifier implements SimpleBuildStep {

	@DataBoundConstructor
	public PublishNotifierStep(String credential, Workspace workspace,
	                           Publish publish) {
		super(credential, workspace, publish);
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public void perform(Run<?, ?> run, FilePath buildWorkspace,
	                    Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		// return early if publish not required
		if (getPublish().isOnlyOnSuccess() && run.getResult() != Result.SUCCESS) {
			return;
		}

		Workspace ws = (Workspace) getWorkspace().clone();

		// Create task
		PublishTask task = new PublishTask(getPublish());
		task.setListener(listener);
		task.setCredential(getCredential(), run.getParent());
		ws = task.setEnvironment(run, ws, buildWorkspace);
		task.setWorkspace(ws);

		// Expand description
		String desc = getPublish().getDescription();
		desc = ws.getExpand().format(desc, false);
		getPublish().setExpandedDesc(desc);

		buildWorkspace.act(task);
	}
}
