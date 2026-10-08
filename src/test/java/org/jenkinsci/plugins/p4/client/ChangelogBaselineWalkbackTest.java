package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import hudson.util.LogTaskListener;
import jenkins.scm.RunWithSCM;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4JENKINS-159: after a credential/ticket outage (server failover or password expiry) a build can fail
 * before p4sync and record no last-built change. The next successful build then finds no baseline on its
 * previous (failed) build and, without the walk-back in PerforceScm.calculateChanges, collapses to "No
 * previous build found" and silently drops every change submitted during the outage.
 *
 * The core tests walk the customer steps from comment 4150071 as a pipeline job and assert the FIX (the
 * recovery build's changelog recovers the outage change) - one drives recovery via a manual build, the
 * other via polling; steps 1-4 are shared in {@link #prepareOutageScenario}. The remaining tests cover the
 * sinceLastSuccess option and that the recovered changelog stays bounded by maxChanges. The walk-back
 * limit itself is covered by FindBaselineBuildTest.
 */
@WithJenkins
class ChangelogBaselineWalkbackTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ChangelogBaselineWalkbackTest-p4root";
	private static final String POST_FAILOVER_CREDENTIAL = "id-postfailover";
	private static final String TOOLS_CREDENTIAL = "id-tools";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	/** Recovery driven by a manual build: the recovery build's changelog must recover the outage change. */
	@Test
	void reproduceCustomerScenarioAfterPasswordExpiry() throws Exception {
		String base = "//depot/walkbackrepro";
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "walkbackrepro");
		String outageChange = prepareOutageScenario(job, base);

		// 5. Recovery build recovers the outage change (walk-back fix) instead of dropping it.
		List<String> recovery = reportedChangeIds(job.scheduleBuild2(0).get());
		assertTrue(recovery.contains(outageChange),
				"FIX: recovery build should recover outage change " + outageChange + ", reported=" + recovery);

		// 6-7. A new commit is reported normally.
		String n1 = submitFile(jenkins, base + "/fileC", "content C");
		assertNotNull(n1);
		List<String> after = reportedChangeIds(job.scheduleBuild2(0).get());
		assertTrue(after.contains(n1), "STEP 7: new commit " + n1 + " should be reported, reported=" + after);
	}

	/** Recovery driven by POLLING: polling detects the change and the triggered build recovers it. */
	@Test
	void reproducePollingScenarioAfterPasswordExpiry() throws Exception {
		String base = "//depot/pollrepro";
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "pollrepro");
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		String outageChange = prepareOutageScenario(job, base);
		LogTaskListener listener = new LogTaskListener(Logger.getLogger("Polling"), Level.INFO);

		// 5. Recovery via polling: it detects the outage change and the triggered build recovers it.
		List<String> recovery = pollAndBuild(job, listener);
		assertNotNull(recovery, "polling should detect the outage change and trigger a recovery build");
		assertTrue(recovery.contains(outageChange),
				"FIX: polling-triggered recovery build should recover outage change " + outageChange + ", reported=" + recovery);

		// 6-7. A new commit is detected by polling and reported.
		String n1 = submitFile(jenkins, base + "/fileC", "content C");
		assertNotNull(n1);
		List<String> after = pollAndBuild(job, listener);
		assertNotNull(after, "polling should detect the new commit and trigger a build");
		assertTrue(after.contains(n1), "STEP 7: new commit " + n1 + " should be reported, reported=" + after);
	}

	/**
	 * A pipeline with a second checkout (another workspace, so another syncID) that keeps working during the
	 * outage: the failed build then carries a TagAction, but none for the main workspace. The walk-back must
	 * step over it to the last build that recorded the main workspace, not stop there and drop the change.
	 */
	@Test
	void walksPastFailedBuildTaggedOnlyByAnotherWorkspace() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), TOOLS_CREDENTIAL);
		assertNotNull(submitFile(jenkins, "//depot/walkbacktools/tool", "tool"));

		String base = "//depot/walkbackmulti";
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "walkbackmulti");
		String outageChange = prepareOutageScenario(job, base, "//depot/walkbacktools/... //${P4_CLIENT}/...");

		List<String> recovery = reportedChangeIds(job.scheduleBuild2(0).get());
		assertTrue(recovery.contains(outageChange),
				"FIX: recovery build should recover outage change " + outageChange + ", reported=" + recovery);
	}

	/**
	 * Guard test: the recovery must also work with the global "changes since last successful build" option
	 * on (PerforceScm.isLastSuccess). That option takes the baseline from getPreviousSuccessfulBuild(), which
	 * skips the failed outage build, so this does not itself exercise the walk-back (the sinceLastSuccess
	 * step is covered by FindBaselineBuildTest); it checks the option does not regress outage recovery.
	 */
	@Test
	void recoversWithChangesSinceLastSuccessEnabled() throws Exception {
		jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class).setLastSuccess(true);

		String base = "//depot/lastsuccessrepro";
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "lastsuccessrepro");
		String outageChange = prepareOutageScenario(job, base);

		List<String> recovery = reportedChangeIds(job.scheduleBuild2(0).get());
		assertTrue(recovery.contains(outageChange),
				"FIX (sinceLastSuccess): recovery build should recover outage change " + outageChange + ", reported=" + recovery);
	}

	/**
	 * When the walk-back finds a distant baseline, the recovery changelog must stay bounded by the global
	 * maxChanges - getChangesFull() reuses the same p4-changes-with-max path as a normal build, so the span
	 * is never dumped whole. Here maxChanges is pinned to 2 with 4 changes in the span; only the 2 most
	 * recent must be reported.
	 */
	@Test
	void walkbackChangelogIsCappedByMaxChanges() throws Exception {
		int cap = 2;
		PerforceScm.DescriptorImpl descriptor = jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);
		JSONObject cfg = new JSONObject();
		cfg.put("maxFiles", PerforceScm.DEFAULT_FILE_LIMIT);
		cfg.put("maxChanges", cap);
		descriptor.configure((StaplerRequest2) null, cfg);
		assertEquals(cap, descriptor.getMaxChanges(), "precondition: maxChanges pinned to " + cap);

		String base = "//depot/capspan";
		String view = base + "/... //${P4_CLIENT}/...";
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "capspan");
		job.setDefinition(new CpsFlowDefinition(pipelineScript(CREDENTIAL, view), false));

		// Baseline build (records the baseline the walk-back will find one step back).
		assertNotNull(submitFile(jenkins, base + "/fileA", "content A"));
		reportedChangeIds(job.scheduleBuild2(0).get());

		// FOUR changes in the outage window - more than the cap of 2.
		assertNotNull(submitFile(jenkins, base + "/fileB", "content B"));
		assertNotNull(submitFile(jenkins, base + "/fileC", "content C"));
		String changeD = submitFile(jenkins, base + "/fileD", "content D");
		String changeE = submitFile(jenkins, base + "/fileE", "content E");
		assertNotNull(changeD);
		assertNotNull(changeE);

		// Break creds, one failed build (no baseline), then fix and recover -> walk-back kicks in.
		swapCredential(CREDENTIAL, "localhost:1");
		assertEquals(Result.FAILURE, job.scheduleBuild2(0).get().getResult(), "outage build must fail");
		swapCredential(CREDENTIAL, p4d.getRshPort());
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), POST_FAILOVER_CREDENTIAL);
		job.setDefinition(new CpsFlowDefinition(pipelineScript(POST_FAILOVER_CREDENTIAL, view), false));

		List<String> recovery = reportedChangeIds(job.scheduleBuild2(0).get());
		assertEquals(cap, recovery.size(), "walk-back changelog should be capped at maxChanges, reported=" + recovery);
		assertTrue(recovery.containsAll(List.of(changeD, changeE)),
				"the two most recent outage changes " + changeD + ", " + changeE + " should be reported, reported=" + recovery);
	}

	/**
	 * Steps 1-4 shared by the outage-recovery tests: run a baseline build, submit the at-risk "outage"
	 * change, break the credential so a build fails (recording no baseline), then fix it with a fresh
	 * credential ID. Returns the outage change the recovery build must not drop; leaves the job ready for
	 * its recovery (step 5). The single failed build puts the baseline one walk-back step away.
	 */
	private String prepareOutageScenario(WorkflowJob job, String base) throws Exception {
		return prepareOutageScenario(job, base, null);
	}

	/**
	 * As {@link #prepareOutageScenario(WorkflowJob, String)}; a non-null {@code toolsView} adds a first
	 * checkout of that view with {@link #TOOLS_CREDENTIAL}, which stays valid during the outage.
	 */
	private String prepareOutageScenario(WorkflowJob job, String base, String toolsView) throws Exception {
		String view = base + "/... //${P4_CLIENT}/...";
		job.setDefinition(new CpsFlowDefinition(pipelineScript(CREDENTIAL, view, toolsView), false));

		// 1. Baseline build; its changelog reports fileA.
		String c1 = submitFile(jenkins, base + "/fileA", "content A");
		assertNotNull(c1);
		List<String> baseline = reportedChangeIds(job.scheduleBuild2(0).get());
		assertTrue(baseline.contains(c1), "STEP 1: baseline build should report change " + c1 + ", reported=" + baseline);

		// The change submitted just before the outage - at risk of being silently dropped.
		String outageChange = submitFile(jenkins, base + "/fileB", "content B");
		assertNotNull(outageChange);

		// 2. Password "expires": swap the credential to a dead port.
		swapCredential(CREDENTIAL, "localhost:1");

		// 3. A build fails before p4sync => no baseline recorded, so the outage change is not in its changelog.
		WorkflowRun failedRun = job.scheduleBuild2(0).get();
		assertEquals(Result.FAILURE, failedRun.getResult(),
				"STEP 3: the outage build must actually FAIL (dead port), otherwise the scenario is not reproduced");
		List<String> failed = reportedChangeIds(failedRun);
		assertFalse(failed.contains(outageChange),
				"STEP 3: failed build must not record the outage change " + outageChange + ", reported=" + failed);

		// 4. Fix creds with a FRESH credential ID and reconfigure the job. syncID is derived from the client
		//    name (not the credential), so the step-1 baseline is still matched. (CREDENTIAL is also restored
		//    so submitFile can keep creating commits.)
		swapCredential(CREDENTIAL, p4d.getRshPort());
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), POST_FAILOVER_CREDENTIAL);
		job.setDefinition(new CpsFlowDefinition(pipelineScript(POST_FAILOVER_CREDENTIAL, view, toolsView), false));

		return outageChange;
	}

	/**
	 * Pipeline script that checks out the given view with the given credential (manual workspace). The
	 * client name is pinned to a fixed value per job (only ${JOB_NAME}, no ${NODE_NAME}/${EXECUTOR_NUMBER})
	 * so the syncID - which is derived from the client name - stays stable across builds. The walk-back
	 * only matches a prior baseline recorded under the SAME syncID, so a volatile client name would defeat
	 * the scenario.
	 */
	private static String pipelineScript(String credential, String view) {
		return pipelineScript(credential, view, null);
	}

	/** As {@link #pipelineScript(String, String)}, preceded by a tools checkout when {@code toolsView} is set. */
	private static String pipelineScript(String credential, String view, String toolsView) {
		String tools = (toolsView == null) ? "" : "  checkout perforce(\n"
				+ "    credential: '" + TOOLS_CREDENTIAL + "',\n"
				+ "    populate: forceClean(quiet: true),\n"
				+ "    workspace: manualSpec(name: 'jenkins-${JOB_NAME}-tools',\n"
				+ "      pinHost: false,\n"
				+ "      spec: clientSpec(view: '" + toolsView + "')))\n";
		return "node {\n"
				+ tools
				+ "  checkout perforce(\n"
				+ "    credential: '" + credential + "',\n"
				+ "    populate: forceClean(quiet: true),\n"
				+ "    workspace: manualSpec(name: 'jenkins-${JOB_NAME}',\n"
				+ "      pinHost: false,\n"
				+ "      spec: clientSpec(view: '" + view + "')))\n"
				+ "}";
	}

	/** Poll once; if changes are found, trigger a build and return its changelog change ids; else null. */
	private List<String> pollAndBuild(WorkflowJob job, LogTaskListener listener) throws Exception {
		if (!job.poll(listener).hasChanges()) {
			return null;
		}
		return reportedChangeIds(job.scheduleBuild2(0).get());
	}

	/** Remove the credential with the given id and recreate it pointing at the given p4port. */
	private void swapCredential(String id, String p4port) throws Exception {
		SystemCredentialsProvider.getInstance().getCredentials().removeIf(
				c -> c instanceof P4PasswordImpl && id.equals(((P4PasswordImpl) c).getId()));
		createCredentials("jenkins", "jenkins", p4port, id);
	}

	private static List<String> reportedChangeIds(RunWithSCM<?, ?> build) {
		List<String> ids = new ArrayList<>();
		for (ChangeLogSet<? extends ChangeLogSet.Entry> set : build.getChangeSets()) {
			if (set instanceof P4ChangeSet) {
				for (P4ChangeEntry entry : ((P4ChangeSet) set).getHistory()) {
					ids.add(entry.getId().toString());
				}
			}
		}
		return ids;
	}
}
