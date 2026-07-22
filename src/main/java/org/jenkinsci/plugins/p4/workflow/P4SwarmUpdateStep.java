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
import org.jenkinsci.plugins.p4.workflow.source.P4SwarmUpdateAction;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.util.Set;
import java.util.logging.Logger;

public class P4SwarmUpdateStep extends Step {
	private static final Logger logger = Logger.getLogger(P4SwarmUpdateStep.class.getName());
	private final String updateMessage;

	@DataBoundConstructor
	public P4SwarmUpdateStep(String updateMessage) {
		this.updateMessage = updateMessage;
	}

	public String getUpdateMessage() {
		return updateMessage;
	}

	@Extension(optional = true)
	@Symbol("swarmUpdate")
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4SwarmUpdate";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Swarm Update";
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

	@Override
	public StepExecution start(StepContext context) {
		return new P4SwarmUpdateStepExecution(this, context);
	}

	private class P4SwarmUpdateStepExecution extends SynchronousStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;
		private final P4SwarmUpdateStep step;

		public P4SwarmUpdateStepExecution(P4SwarmUpdateStep p4SwarmUpdateStep, StepContext context) {
			super(context);
			this.step = p4SwarmUpdateStep;
		}

		@Override
		protected Void run() throws Exception {
			logger.info("Running p4SwarmUpdateStep");
			Run run = getContext().get(Run.class);
			run.addAction(new P4SwarmUpdateAction(getUpdateMessage()));
			return null;
		}
	}
}
