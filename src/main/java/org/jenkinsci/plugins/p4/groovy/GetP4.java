package org.jenkinsci.plugins.p4.groovy;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.build.ExecutorHelper;
import org.jenkinsci.plugins.p4.build.NodeHelper;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class GetP4 extends Builder implements SimpleBuildStep {

	private final String credential;
	private final Workspace workspace;

	private P4Groovy p4Groovy;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public P4Groovy getP4Groovy() {
		return p4Groovy;
	}

	@DataBoundConstructor
	public GetP4(String credential, Workspace workspace) {
		this.credential = credential;
		this.workspace = workspace;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public void perform(Run<?, ?> run, FilePath buildWorkspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		// Set environment
		EnvVars envVars = run.getEnvironment(listener);
		String nodeName = NodeHelper.getNodeName(buildWorkspace);
		envVars.put("NODE_NAME", envVars.get("NODE_NAME", nodeName));
		String executor = ExecutorHelper.getExecutorID(buildWorkspace);
		envVars.put("EXECUTOR_NUMBER", envVars.get("EXECUTOR_NUMBER", executor));

		workspace.setExpand(envVars);
		workspace.setRootPath(buildWorkspace.getRemote());

		// Create Task
		GetP4Task task = new GetP4Task(run, credential, workspace, buildWorkspace, listener);

		p4Groovy = buildWorkspace.act(task);
	}
}