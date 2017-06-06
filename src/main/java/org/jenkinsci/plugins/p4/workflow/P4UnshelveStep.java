package org.jenkinsci.plugins.p4.workflow;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.p4.unshelve.UnshelveBuilder;
import org.jenkinsci.plugins.p4.unshelve.UnshelveBuilderStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

public class P4UnshelveStep extends Step {

	private final String shelf;
	private final String resolve;

	@DataBoundConstructor
	public P4UnshelveStep(String shelf, String resolve) {
		this.shelf = shelf;
		this.resolve = resolve;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new P4UnshelveStepExecution(this, context);
	}

	public String getShelf() {
		return shelf;
	}

	public String getResolve() {
		return resolve;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "p4unshelve";
		}

		@Override
		public String getDisplayName() {
			return "P4 Unshelve";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
		}

		public ListBoxModel doFillResolveItems() {
			return UnshelveBuilder.DescriptorImpl.doFillResolveItems();
		}
	}

	public static class P4UnshelveStepExecution extends SynchronousNonBlockingStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		private transient P4UnshelveStep step;

		protected P4UnshelveStepExecution(P4UnshelveStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			UnshelveBuilderStep unshelve = new UnshelveBuilderStep(step.getShelf(), step.getResolve());
			unshelve.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
			return null;
		}
	}
}
