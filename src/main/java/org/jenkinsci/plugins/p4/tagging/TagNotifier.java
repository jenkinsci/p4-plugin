package org.jenkinsci.plugins.p4.tagging;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Logger;

public class TagNotifier extends Notifier {

	private static final Logger logger = Logger.getLogger(TagNotifier.class.getName());

	public final String rawLabelName;
	public final String rawLabelDesc;
	public final boolean onlyOnSuccess;

	@DataBoundConstructor
	public TagNotifier(String rawLabelName, String rawLabelDesc, boolean onlyOnSuccess) {
		this.rawLabelName = rawLabelName;
		this.rawLabelDesc = rawLabelDesc;
		this.onlyOnSuccess = onlyOnSuccess;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

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
			FilePath workspace = build.getWorkspace();
			tagAction.labelBuild(listener, name, description, workspace);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private TagAction getTagAction(EnvVars env, AbstractBuild<?, ?> build) {
		TagAction tagAction = TagAction.getLastAction(build);

		// process promoted builds?
		if (tagAction == null) {
			String jobName = env.get("PROMOTED_JOB_NAME");
			if (jobName == null || jobName.isEmpty()) {
				logger.warning("No tag information; not a promotion job.");
				return tagAction;
			}

			String buildNumber = env.get("PROMOTED_NUMBER");
			if (buildNumber == null || buildNumber.isEmpty()) {
				logger.warning("No tag information; not a promotion job.");
				return tagAction;
			}

			AbstractProject<?, ?> project;
			Jenkins j = Jenkins.get();
			project = j.getItemByFullName(jobName, AbstractProject.class);
			if (project == null) {
				logger.warning("No project; is it a valid Perforce job?");
				return tagAction;
			}

			int buildNum = Integer.parseInt(buildNumber);
			build = project.getBuildByNumber(buildNum);
			if (build == null) {
				logger.warning("No build number; is it a valid Perforce job?");
				return tagAction;
			}

			tagAction = build.getAction(TagAction.class);
			if (tagAction == null) {
				logger.warning("No tag information; is it a valid Perforce job?");
				return tagAction;
			}
		}
		return tagAction;
	}

	public static DescriptorImpl descriptor() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorByType(TagNotifier.DescriptorImpl.class);
	}

	@Extension
	@Symbol("label")
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce: Label build";
		}

	}
}
