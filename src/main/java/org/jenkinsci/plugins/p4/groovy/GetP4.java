package org.jenkinsci.plugins.p4.groovy;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.credentials.P4InvalidCredentialException;
import org.jenkinsci.plugins.p4.tasks.AbstractTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class GetP4 extends Builder implements SimpleBuildStep {

	private final String credential;

	private Workspace workspace;
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
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath buildWorkspace, @NonNull Launcher launcher, @NonNull TaskListener listener)
			throws InterruptedException, IOException {

		// Setup workspace used for the GroovyTask
		workspace = AbstractTask.setup(run, workspace, buildWorkspace, listener);

		try {
			GetP4Task task = new GetP4Task(run, credential, workspace, buildWorkspace, listener);
			p4Groovy = buildWorkspace.act(task);
		} catch (P4InvalidCredentialException ex) {
			// credential not found.
			throw new IOException(ex.getMessage(), ex);
		}
	}
}