package org.jenkinsci.plugins.p4.workflow;

import javax.inject.Inject;

import org.jenkinsci.plugins.p4.unshelve.UnshelveBuilderStep;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

public class P4UnshelveStep extends AbstractStepImpl {

	private final String shelf;
	private final String resolve;

	@DataBoundConstructor
	public P4UnshelveStep(String shelf, String resolve) {
		this.shelf = shelf;
		this.resolve = resolve;
	}

	public String getShelf() {
		return shelf;
	}

	public String getResolve() {
		return resolve;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(P4UnshelveStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "p4unshelve";
		}

		@Override
		public String getDisplayName() {
			return "P4 Unshelve";
		}

	}

	public static class P4UnshelveStepExecution extends AbstractSynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		@Inject
		private transient P4UnshelveStep step;
		@StepContextParameter
		private transient Run<?, ?> run;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient Launcher launcher;

		@Override
		protected Void run() throws Exception {
			UnshelveBuilderStep unshelve = new UnshelveBuilderStep(step.getShelf(), step.getResolve());
			unshelve.perform(run, workspace, launcher, listener);
			return null;
		}

	}
}
