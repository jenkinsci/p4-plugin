package org.jenkinsci.plugins.p4.build;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.P4SCMHead;
import org.jenkinsci.plugins.p4.scm.P4SCMRevision;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Extension()
public class P4EnvironmentContributor extends EnvironmentContributor {
	private static Logger logger = Logger.getLogger(P4EnvironmentContributor.class.getName());

	@Override
	public void buildEnvironmentFor(@NonNull Run run, @NonNull EnvVars env, @NonNull TaskListener listener) {
		TagAction tagAction = TagAction.getLastAction(run);
		buildEnvironment(tagAction, env);
		injectSwarmEnvVars(run, env);
	}

	public static void buildEnvironment(TagAction tagAction, Map<String, String> map) {
		// parts of Jenkins passes EnvVars as Map<String,String>
		EnvVars env = new EnvVars(map);
		buildEnvironment(tagAction, env);
		map.putAll(env);
	}

	private static void buildEnvironment(TagAction tagAction, EnvVars env) {
		if (tagAction == null) {
			return;
		}

		// Set P4_CHANGELIST value
		if (tagAction.getRefChanges() != null && !tagAction.getRefChanges().isEmpty()) {
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

		// Set P4_ROOT value
		if (tagAction.getWorkspace() != null) {
			Workspace tagWorkspace = tagAction.getWorkspace();
			if (tagWorkspace != null && tagWorkspace.getRootPath() != null) {
				env.put("P4_ROOT", tagWorkspace.getRootPath());
			}
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
			env.put("P4_REVIEW_TYPE", review.getStatus().toString());
		}

		// Set P4_TICKET connection
		Jenkins j = Jenkins.get();
		if (j != null) {
			@SuppressWarnings("unchecked")
			Descriptor<SCM> scm = j.getDescriptor(PerforceScm.class);
			PerforceScm.DescriptorImpl p4scm = (PerforceScm.DescriptorImpl) scm;

			if (p4scm != null && !p4scm.isHideTicket()) {
				// JENKINS-60141: Refactored to reduce number of calls to login -s
				String ticket = tagAction.getTicket();
				if (ticket != null) {
					env.put("P4_TICKET", ticket);
				}
			}
		}

		// JENKINS-37442: Make the log file name available
		if (tagAction.getChangelog() != null) {
			String changelog = tagAction.getChangelog().getAbsolutePath();
			changelog = StringUtils.defaultIfBlank(changelog, "Not-set");
			env.put("HUDSON_CHANGELOG_FILE", changelog);
		}

		// JENKINS-39107: Make the jenkinsPath available
		if (tagAction.getJenkinsPath() != null) {
			String jenkinsPath = tagAction.getJenkinsPath();
			env.put("JENKINSFILE_PATH", jenkinsPath);
		}
	}

	private static void injectSwarmEnvVars(Run run, EnvVars env) {
		List<SCMRevisionAction> actions = run.getActions(SCMRevisionAction.class);

		for (SCMRevisionAction action : actions) {
			if (action == null) {
				continue;
			}
			SCMRevision revision = action.getRevision();
			if (!(revision instanceof P4SCMRevision)) {
				continue;
			}

			SCMHead head = revision.getHead();
			if (!(head instanceof P4SCMHead)) {
				continue;
			}
			Map<String, String> swarmParameters = ((P4SCMHead) head).getSwarmParams();
			if(swarmParameters == null || swarmParameters.isEmpty()) {
				continue;
			}
			String swarmBranch = swarmParameters.get(ReviewProp.SWARM_BRANCH.toString());
			if (StringUtils.isNotEmpty(swarmBranch)) {
				logger.info("Set swarm branch : " + swarmBranch);
				env.put("P4_Swarm_Branch", swarmBranch);
			}
			String change = swarmParameters.get(ReviewProp.P4_CHANGE.toString());
			if (StringUtils.isNotEmpty(change)) {
				logger.info("Set change : " + change);
				env.put(ReviewProp.P4_CHANGE.toString(), change);
			}
		}
	}
}
