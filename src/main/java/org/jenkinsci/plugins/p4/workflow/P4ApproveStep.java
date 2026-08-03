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
import org.jenkinsci.plugins.p4.review.ApproveNotifier;
import org.jenkinsci.plugins.p4.review.ApproveNotifierStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.Set;

public class P4ApproveStep extends Step {

	private final String credential;
	private final String review;
	private final String status;

	private String description;

	public String getCredential() {
		return credential;
	}

	public String getReview() {
		return review;
	}

	public String getStatus() {
		return status;
	}

	public String getDescription() {
		return description;
	}

	@DataBoundConstructor
	public P4ApproveStep(String credential, String review, String status) {
		this.credential = credential;
		this.review = review;
		this.status = status;
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public StepExecution start(StepContext context) {
		return new P4ApproveStepExecution(this, context);
	}

	@Extension(optional = true)
	@Symbol("approve")
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4approve";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 ApproveImpl Review";
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

		public ListBoxModel doFillStatusItems() {
			return ApproveNotifier.DescriptorImpl.doFillStatusItems();
		}
	}

	public static class P4ApproveStepExecution extends SynchronousNonBlockingStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		private transient P4ApproveStep step = null;

		protected P4ApproveStepExecution(P4ApproveStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			ApproveNotifierStep notifier = new ApproveNotifierStep(step.getCredential(), step.getReview(), step.getStatus());
			notifier.setDescription(step.getDescription());
			notifier.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
			return null;
		}
	}
}
