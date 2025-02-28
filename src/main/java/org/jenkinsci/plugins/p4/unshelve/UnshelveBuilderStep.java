package org.jenkinsci.plugins.p4.unshelve;

import edu.umd.cs.findbugs.annotations.NonNull;
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

	public UnshelveBuilderStep(String credential, Workspace workspace, String shelf, String resolve, boolean tidy, boolean ignoreEmpty) {
		super(shelf, resolve, tidy, ignoreEmpty);
		this.credential = credential;
		this.workspace = workspace;
	}

	@Deprecated
	public UnshelveBuilderStep(String credential, Workspace workspace, String shelf, String resolve, boolean tidy) {
		this(null, null, shelf, resolve, tidy, false);
	}

	@Deprecated
	public UnshelveBuilderStep(String shelf, String resolve) {
		super(shelf, resolve, false, false);
	}

	@Override
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath buildWorkspace, @NonNull Launcher launcher, @NonNull TaskListener listener)
			throws InterruptedException, IOException {

		TagAction tagAction = TagAction.getLastAction(run);
		credential = (credential == null) ? tagAction.getCredential() : credential;
		workspace = (workspace == null) ? tagAction.getWorkspace() : workspace;

		unshelve(run, credential, workspace, buildWorkspace, listener);
	}
}
