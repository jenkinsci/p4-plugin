package org.jenkinsci.plugins.p4.build;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.tagging.TagAction;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Extension()
public class P4EnvironmentContributor extends EnvironmentContributor {

	@Override
	public void buildEnvironmentFor(Run run, EnvVars env, TaskListener listener) throws IOException, InterruptedException {

		TagAction tagAction = TagAction.getLastAction(run);
		buildEnvironment(tagAction, env);

		File changelogFile = getCurrentChangelogFile(run.getRootDir());
		String changelogFilename = changelogFile.getAbsolutePath();
		env.put("HUDSON_CHANGELOG_FILE", StringUtils.defaultIfBlank(changelogFilename, "Not-set"));
	}

	public static void buildEnvironment(TagAction tagAction, Map<String, String> map) {
		// parts of Jenkins passes EnvVars as Map<String,String>
		if (map instanceof EnvVars) {
			EnvVars env = (EnvVars) map;
			buildEnvironment(tagAction, env);
		}
	}

	public static void buildEnvironment(TagAction tagAction, EnvVars env) {
		if (tagAction == null) {
			return;
		}

		// Set P4_CHANGELIST value
		if (tagAction.getRefChanges() != null) {
			String change = tagAction.getRefChange().toString();
			env.put("P4_CHANGELIST", change);
		}

		// Set P4_CLIENT workspace value
		if (tagAction.getClient() != null) {
			String client = tagAction.getClient();
			env.put("P4_CLIENT", client);
		}

		// Set P4_PORT connection
		if (tagAction.getPort() != null) {
			String port = tagAction.getPort();
			env.put("P4_PORT", port);
		}

		// Set P4_USER connection
		if (tagAction.getUser() != null) {
			String user = tagAction.getUser();
			env.put("P4_USER", user);
		}

		// Set P4_REVIEW connection
		if (tagAction.getReview() != null) {
			P4Review review = tagAction.getReview();
			env.put("P4_REVIEW", review.getId());
			env.put("P4_REVIEW_STATUS", review.getStatus().toString());
		}

		// Set P4_TICKET connection
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			@SuppressWarnings("unchecked")
			Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
			PerforceScm.DescriptorImpl p4scm = (PerforceScm.DescriptorImpl) scm;

			if (tagAction.getTicket() != null && !p4scm.isHideTicket()) {
				String ticket = tagAction.getTicket();
				env.put("P4_TICKET", ticket);
			}
		}
	}

	private File getCurrentChangelogFile(File rootDir) {
		File changelogFile;

		int i = 0;
		File next = new File(rootDir, "changelog" + i + ".xml");
		do {
			changelogFile = next;
			next = new File(rootDir, "changelog" + i + ".xml");
			i++;
		} while (next.exists());

		return changelogFile;
	}
}
