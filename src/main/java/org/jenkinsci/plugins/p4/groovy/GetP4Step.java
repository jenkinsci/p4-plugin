package org.jenkinsci.plugins.p4.groovy;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.util.Set;

public class GetP4Step extends Step {

	private final String credential;
	private final Workspace workspace;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	@DataBoundConstructor
	public GetP4Step(String credential, Workspace workspace) {
		this.credential = credential;
		this.workspace = workspace;
	}

	@Extension(optional = true)
	@Symbol("p4groovy")
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Groovy";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
		}

		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}
	}

	@Override
	public StepExecution start(StepContext context) {
		return new Execution(this, context);
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<P4Groovy> {

		@Serial
		private static final long serialVersionUID = 1L;

		private transient GetP4Step step = null;

		Execution(GetP4Step step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected P4Groovy run() throws Exception {
			GetP4 p4Groovy = new GetP4(step.getCredential(), step.getWorkspace());
			p4Groovy.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
			return p4Groovy.getP4Groovy();
		}
	}
}