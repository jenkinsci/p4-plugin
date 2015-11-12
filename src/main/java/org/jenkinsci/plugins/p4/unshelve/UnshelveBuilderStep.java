package org.jenkinsci.plugins.p4.unshelve;

import java.io.IOException;

import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;

public class UnshelveBuilderStep extends UnshelveBuilder implements SimpleBuildStep {

	public UnshelveBuilderStep(String shelf, String resolve) {
		super(shelf, resolve);
	}

	@Override
	public void perform(Run<?, ?> run, FilePath buildWorkspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		TagAction tagAction = (TagAction) run.getAction(TagAction.class);

		String credential = tagAction.getCredential();
		Workspace workspace = tagAction.getWorkspace();

		unshelve(run, credential, workspace, buildWorkspace, listener);
	}
}
