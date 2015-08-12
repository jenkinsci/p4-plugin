package org.jenkinsci.plugins.p4.workflow;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;

import javax.inject.Inject;

import org.jenkinsci.plugins.p4.tagging.TagNotifierStep;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

public class P4TaggingStep extends AbstractStepImpl {

	private final String rawLabelName;
	private final String rawLabelDesc;

	@DataBoundConstructor
	public P4TaggingStep(String rawLabelName, String rawLabelDesc) {
		this.rawLabelName = rawLabelName;
		this.rawLabelDesc = rawLabelDesc;
	}

	public String getRawLabelName() {
		return rawLabelName;
	}

	public String getRawLabelDesc() {
		return rawLabelDesc;
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(P4TaggingStepExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "p4tag";
		}

		@Override
		public String getDisplayName() {
			return "P4 Tag";
		}

	}

	public static class P4TaggingStepExecution extends
			AbstractSynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		@Inject
		private transient P4TaggingStep step;
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
			TagNotifierStep notifier = new TagNotifierStep(
					step.getRawLabelName(), step.getRawLabelDesc(), false);
			notifier.perform(run, workspace, launcher, listener);
			return null;
		}

	}

}
