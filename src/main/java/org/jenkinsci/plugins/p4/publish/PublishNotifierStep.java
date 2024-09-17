package org.jenkinsci.plugins.p4.publish;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
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

		Workspace ws = getWorkspace().deepClone();

		// Create task
		PublishTask task = new PublishTask(getCredential(), run, listener, getPublish());
		ws = task.setEnvironment(run, ws, buildWorkspace);
		task.setWorkspace(ws);

		// Expand description
		String desc = getPublish().getDescription();
		desc = ws.getExpand().format(desc, false);
		getPublish().setExpandedDesc(desc);

		String publishedChangeID = buildWorkspace.act(task);
		if (StringUtils.isNotEmpty(publishedChangeID)) {
			run.addAction(new P4PublishEnvironmentContributingAction(publishedChangeID));
		}

		cleanupPerforceClient(run, buildWorkspace, listener);
	}
}
