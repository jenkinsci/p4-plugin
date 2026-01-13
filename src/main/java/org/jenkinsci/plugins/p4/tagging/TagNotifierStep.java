package org.jenkinsci.plugins.p4.tagging;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.p4.workspace.Expand;

import java.io.IOException;
import java.util.logging.Logger;

public class TagNotifierStep extends TagNotifier implements SimpleBuildStep {

	private static final Logger logger = Logger.getLogger(TagNotifierStep.class.getName());

	public TagNotifierStep(String rawLabelName, String rawLabelDesc, boolean onlyOnSuccess) {
		super(rawLabelName, rawLabelDesc, onlyOnSuccess);
	}

	@Override
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener)
			throws IOException {

		// return early if label not required
		if (onlyOnSuccess && run.getResult() != Result.SUCCESS) {
			return;
		}
		try {
			// Expand label name and description
			EnvVars env = run.getEnvironment(listener);
			Expand expand = new Expand(env);
			String name = expand.format(rawLabelName, false);
			String description = expand.format(rawLabelDesc, false);

			// Get TagAction and check for promoted builds
			TagAction tagAction = getTagAction(env, run);

			// Label with TagAction
			tagAction.labelBuild(listener, name, description, workspace);
		} catch (Exception e) {
			final String err = "P4: Could not label: " + e;
			logger.severe(err);
			throw new AbortException(err);
		}
	}

	private TagAction getTagAction(EnvVars env, Run<?, ?> run) {
		TagAction tagAction = TagAction.getLastAction(run);

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

			Jenkins j = Jenkins.get();
			Job<?, ?> job = j.getItemByFullName(jobName, Job.class);
			if (job == null) {
				logger.warning("No job information; is it a valid Perforce job?");
				return tagAction;
			}

			int buildNum = Integer.parseInt(buildNumber);
			run = job.getBuildByNumber(buildNum);
			if (run == null) {
				logger.warning("No build number; is it a valid Perforce job?");
				return tagAction;
			}

			tagAction = run.getAction(TagAction.class);

			if (tagAction == null) {
				logger.warning("No tag information; is it a valid Perforce job?");
				return tagAction;
			}
		}
		return tagAction;
	}
}
