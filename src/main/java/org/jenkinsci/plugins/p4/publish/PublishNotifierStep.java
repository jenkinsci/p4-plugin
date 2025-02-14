package org.jenkinsci.plugins.p4.publish;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
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
import java.util.List;
import java.util.Map;

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

		Map<String, List<String>> idArtifactsMap = buildWorkspace.act(task);
		Map.Entry<String, List<String>> entry = idArtifactsMap.entrySet().iterator().next();
		String publishedChangeID = entry.getKey();
		if (StringUtils.isNotEmpty(publishedChangeID)) {
			run.addAction(new P4PublishEnvironmentContributingAction(publishedChangeID));
		}

		List<String> artifacts = entry.getValue();
		if (!artifacts.isEmpty()) {
			Map<String, String> artifactsDirMap = mapArtifactsToLocalPaths(artifacts, buildWorkspace);
			if (!artifactsDirMap.isEmpty()) {
				run.pickArtifactManager().archive(buildWorkspace, launcher, (BuildListener) listener, artifactsDirMap);
			}
		}

		cleanupPerforceClient(run, buildWorkspace, listener);
	}
}
