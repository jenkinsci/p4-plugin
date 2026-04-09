package org.jenkinsci.plugins.p4.workflow;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.unshelve.UnshelveBuilder;
import org.jenkinsci.plugins.p4.unshelve.UnshelveBuilderStep;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.Set;

public class P4UnshelveStep extends Step {

	private final String credential;
	private final Workspace workspace;
	private final String shelf;
	private final String resolve;
	private final boolean tidy;
	private final boolean ignoreEmpty;

	@DataBoundConstructor
	public P4UnshelveStep(String credential, Workspace workspace, String shelf, String resolve, boolean tidy, boolean ignoreEmpty) {
		this.credential = credential;
		this.workspace = workspace;
		this.shelf = shelf;
		this.resolve = resolve;
		this.tidy = tidy;
		this.ignoreEmpty = ignoreEmpty;
	}

	@Deprecated
	public P4UnshelveStep(String credential, Workspace workspace, String shelf, String resolve, boolean tidy) {
		this(null, null, shelf, resolve, tidy, false);
	}

	@Deprecated
	public P4UnshelveStep(String shelf, String resolve) {
		this(null, null, shelf, resolve, false, false);
	}

	@Override
	public StepExecution start(StepContext context) {
		return new P4UnshelveStepExecution(this, context);
	}

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public String getShelf() {
		return shelf;
	}

	public String getResolve() {
		return resolve;
	}

	public boolean isTidy() {
		return tidy;
	}

	public boolean isIgnoreEmpty() {
		return ignoreEmpty;
	}

	@Extension(optional = true)
	@Symbol("unshelve")
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4unshelve";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Unshelve";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
		}

		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}

		public FormValidation doCheckCredential(@AncestorInPath Item project, @QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(project, value);
		}

		public ListBoxModel doFillResolveItems() {
			return UnshelveBuilder.DescriptorImpl.doFillResolveItems();
		}
	}

	public static class P4UnshelveStepExecution extends SynchronousNonBlockingStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		private transient P4UnshelveStep step = null;

		protected P4UnshelveStepExecution(P4UnshelveStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			UnshelveBuilderStep unshelve = new UnshelveBuilderStep(step.getCredential(), step.getWorkspace(), step.getShelf(), step.getResolve(), step.isTidy(), step.isIgnoreEmpty());
			unshelve.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
			return null;
		}
	}
}
