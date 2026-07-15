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
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.PublishNotifierStep;
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

public class P4PublishStep extends Step {

	private final String credential;
	private final Workspace workspace;
	private final Publish publish;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Publish getPublish() {
		return publish;
	}

	@DataBoundConstructor
	public P4PublishStep(String credential, Workspace workspace, Publish publish) {
		this.credential = credential;
		this.workspace = workspace;
		this.publish = publish;
	}

	@Override
	public StepExecution start(StepContext context) {
		return new P4PublishStepExecution(this, context);
	}

	@Extension(optional = true)
	@Symbol("publish")
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4publish";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Publish";
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
	}

	public static class P4PublishStepExecution extends SynchronousNonBlockingStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		private transient P4PublishStep step = null;

		protected P4PublishStepExecution(P4PublishStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			PublishNotifierStep notifier = new PublishNotifierStep(step.getCredential(), step.getWorkspace(), step.getPublish());
			notifier.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
			return null;
		}
	}
}
