package org.jenkinsci.plugins.p4.unshelve;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;

public class UnshelveBuilderStep extends UnshelveBuilder implements SimpleBuildStep {

	private String credential;
	private Workspace workspace;

	public UnshelveBuilderStep(String credential, Workspace workspace, String shelf, String resolve, boolean tidy) {
		super(shelf, resolve, tidy);
		this.credential = credential;
		this.workspace = workspace;
	}

	@Deprecated
	public UnshelveBuilderStep(String shelf, String resolve) {
		super(shelf, resolve, false);
	}

	@Override
	public void perform(Run<?, ?> run, FilePath buildWorkspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		TagAction tagAction = TagAction.getLastAction(run);
		credential = (credential == null) ? tagAction.getCredential() : credential;
		workspace = (workspace == null) ? tagAction.getWorkspace() : workspace;

		unshelve(run, credential, workspace, buildWorkspace, listener);
	}
}
