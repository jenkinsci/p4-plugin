package org.jenkinsci.plugins.p4.tagging;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class TagNotifier extends Notifier {

	protected static final Logger LOGGER = Logger.getLogger(TagNotifier.class
			.getName());

	public final String rawLabelName;
	public final String rawLabelDesc;
	public final boolean onlyOnSuccess;

	@DataBoundConstructor
	public TagNotifier(String rawLabelName, String rawLabelDesc,
			boolean onlyOnSuccess) {
		this.rawLabelName = rawLabelName;
		this.rawLabelDesc = rawLabelDesc;
		this.onlyOnSuccess = onlyOnSuccess;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException {

		PrintStream log = listener.getLogger();

		// return early if label not required
		if (onlyOnSuccess && build.getResult() != Result.SUCCESS) {
			return true;
		}

		// fetch build environment
		EnvVars environment;
		try {
			environment = build.getEnvironment(listener);
		} catch (IOException e) {
			log.println("Could not load build environment.");
			return false;
		}

		TagAction tagAction = (TagAction) build.getAction(TagAction.class);

		// process promoted builds?
		if (tagAction == null) {
			String jobName = environment.get("PROMOTED_JOB_NAME");
			if (jobName == null || jobName.isEmpty()) {
				log.println("No tag information; not a promotion job.");
				return false;
			}

			String buildNumber = environment.get("PROMOTED_NUMBER");
			if (buildNumber == null || buildNumber.isEmpty()) {
				log.println("No tag information; not a promotion job.");
				return false;
			}

			AbstractProject<?, ?> project;
			Jenkins j = Jenkins.getInstance();
			project = j.getItemByFullName(jobName, AbstractProject.class);

			int buildNum = Integer.parseInt(buildNumber);
			build = (AbstractBuild<?, ?>) project.getBuildByNumber(buildNum);
			tagAction = (TagAction) build.getAction(TagAction.class);

			if (tagAction == null) {
				log.println("No tag information; is it a valid Perforce job?");
				return false;
			}
		}

		// label build
		listener.getLogger().println("Tagging build: " + rawLabelName);
		try {
			tagAction.labelBuild(rawLabelName, rawLabelDesc);
		} catch (Exception e) {
			log.println(e.getMessage());
			return false;
		}

		return true;
	}

	public static DescriptorImpl descriptor() {
		return Jenkins.getInstance().getDescriptorByType(
				TagNotifier.DescriptorImpl.class);
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Perforce: Label build";
		}

	}
}
