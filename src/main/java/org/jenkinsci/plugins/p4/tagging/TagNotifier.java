package org.jenkinsci.plugins.p4.tagging;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.p4.workspace.Expand;
import org.kohsuke.stapler.DataBoundConstructor;

public class TagNotifier extends Notifier {

	protected static final Logger LOGGER = Logger.getLogger(TagNotifier.class
			.getName());

	public final String rawLabelName;
	public final String rawLabelDesc;
	public final boolean onlyOnSuccess;

	private TaskListener listener;

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

		// Enable logging
		this.listener = listener;

		// return early if label not required
		if (onlyOnSuccess && build.getResult() != Result.SUCCESS) {
			return true;
		}
		try {
			// Expand label name and description
			EnvVars env = build.getEnvironment(listener);
			Expand expand = new Expand(env);
			String name = expand.format(rawLabelName, false);
			String description = expand.format(rawLabelDesc, false);

			// Get TagAction and check for promoted builds
			TagAction tagAction = getTagAction(env, build);

			// Label with TagAction
			tagAction.labelBuild(listener, name, description);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private TagAction getTagAction(EnvVars env, AbstractBuild<?, ?> build) {
		TagAction tagAction = (TagAction) build.getAction(TagAction.class);

		// process promoted builds?
		if (tagAction == null) {
			String jobName = env.get("PROMOTED_JOB_NAME");
			if (jobName == null || jobName.isEmpty()) {
				log("No tag information; not a promotion job.");
				return tagAction;
			}

			String buildNumber = env.get("PROMOTED_NUMBER");
			if (buildNumber == null || buildNumber.isEmpty()) {
				log("No tag information; not a promotion job.");
				return tagAction;
			}

			AbstractProject<?, ?> project;
			Jenkins j = Jenkins.getInstance();
			project = j.getItemByFullName(jobName, AbstractProject.class);

			int buildNum = Integer.parseInt(buildNumber);
			build = (AbstractBuild<?, ?>) project.getBuildByNumber(buildNum);
			tagAction = (TagAction) build.getAction(TagAction.class);

			if (tagAction == null) {
				log("No tag information; is it a valid Perforce job?");
				return tagAction;
			}
		}
		return tagAction;
	}

	private void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
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
