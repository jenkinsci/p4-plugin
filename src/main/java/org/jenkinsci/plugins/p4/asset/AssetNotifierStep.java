package org.jenkinsci.plugins.p4.asset;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.tasks.BuildStepMonitor;

import java.io.IOException;

import jenkins.tasks.SimpleBuildStep;

import org.jenkinsci.plugins.p4.asset.AssetNotifier;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.tasks.PublishTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

public class AssetNotifierStep extends AssetNotifier implements SimpleBuildStep {

	@DataBoundConstructor
	public AssetNotifierStep(String credential, Workspace workspace,
			Publish publish) {
		super(credential, workspace, publish);
	}

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
		try {
			EnvVars envVars = run.getEnvironment(listener);
			ws.setExpand(envVars);
			ws.setRootPath(buildWorkspace.getRemote());
			String desc = getPublish().getDescription();
			desc = ws.getExpand().format(desc, false);
			getPublish().setExpandedDesc(desc);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Create task
		PublishTask task = new PublishTask(getPublish());
		task.setListener(listener);
		task.setCredential(getCredential());
		task.setWorkspace(ws);

		buildWorkspace.act(task);
	}
}
