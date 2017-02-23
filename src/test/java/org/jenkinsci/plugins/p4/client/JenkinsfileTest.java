package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JenkinsfileTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(FreeStyleTest.class.getName());
	private static final String P4ROOT = "tmp-JenkinsfileTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, VERSION);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
	}

	@Test
	public void testBasicJenkinsfile() throws Exception {

		String content = ""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content);

		submitFile(jenkins, "//depot/Data/j001", "Content");

		// Manual workspace spec definition
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// Get current change
		ClientHelper p4 = new ClientHelper(CREDENTIAL, null, client, "none");
		int head = Integer.parseInt(p4.getCounter("change"));

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "basicJenkinsfile");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Build 1
		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4_CHANGELIST: " + head, run);

		// Make changes for polling
		submitFile(jenkins, "//depot/Data/j002", "Content");

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		// Test trigger
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals(2, job.getLastBuild().getNumber());
		List<String> log = job.getLastBuild().getLog(1000);
		assertTrue(log.contains("P4_CHANGELIST: " + (head + 1)));

		assertEquals(2, job.getLastBuild().getChangeSets().size());
	}

	@Test
	public void testDiffClients() throws Exception {

		String content = ""
				+ "node {\n"
				+ "   checkout([$class: 'PerforceScm', credential: '" + CREDENTIAL + "',"
				+ "      populate: [$class: 'AutoCleanImpl'],\n"
				+ "      workspace: [$class: 'ManualWorkspaceImpl', name: 'jenkins-${NODE_NAME}-${JOB_NAME}-src',\n"
				+ "         spec: [view: '//depot/Data/... //jenkins-${NODE_NAME}-${JOB_NAME}-src/...']]])\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content);

		submitFile(jenkins, "//depot/Data/j001", "Content");

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// Get current change
		ClientHelper p4 = new ClientHelper(CREDENTIAL, null, client, "none");
		int head = Integer.parseInt(p4.getCounter("change"));

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "diffClientsJenkinsfile");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Build 1
		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run);
		assertEquals(head, Integer.parseInt(p4.getCounter("change")));

		// Make changes for trigger
		submitFile(jenkins, "//depot/Data/j002", "Content");

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		assertEquals(1, job.getLastBuild().getNumber());

		// Test trigger
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals(2, job.getLastBuild().getNumber());
		assertEquals(head + 1, Integer.parseInt(p4.getCounter("change")));

		assertEquals(1, job.getLastBuild().getChangeSets().size());
	}

	@Test
	public void testMulitSync() throws Exception {

		String content1 = ""
				+ "node {\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1',\n"
				+ "      depotPath: '//depot/Data',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '17', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '13', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content1);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "multiSync");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Build 1
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run1);
		assertEquals(1, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: 17", run1);
		jenkins.assertLogContains("P4 Task: syncing files at change: 13", run1);

		String content2 = ""
				+ "node {\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1',\n"
				+ "      depotPath: '//depot/Data',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '18', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '40', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content2);

		// Build 2
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run2);
		assertEquals(2, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: 18", run2);
		jenkins.assertLogContains("P4 Task: syncing files at change: 40", run2);
		jenkins.assertLogContains("Found last change 17 on syncID jenkins-NODE_NAME-multiSync-1", run2);
		jenkins.assertLogContains("Found last change 13 on syncID jenkins-NODE_NAME-multiSync-2", run2);
	}

	@Test
	public void testMulitSyncPolling() throws Exception {

		String content1 = ""
				+ "node {\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1',\n"
				+ "      depotPath: '//depot/Data',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '17', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '13', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content1);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "multiSyncPoll");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Build 1
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run1);
		assertEquals(1, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: 17", run1);
		jenkins.assertLogContains("P4 Task: syncing files at change: 13", run1);

		String content2 = ""
				+ "node {\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1',\n"
				+ "      depotPath: '//depot/Data',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '14', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content2);

		// Get latest change
		ClientHelper p4 = new ClientHelper(CREDENTIAL, null, client, "none");
		int head = Integer.parseInt(p4.getCounter("change"));

		// Build 2
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run2);
		assertEquals(2, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: " + head, run2);
		jenkins.assertLogContains("P4 Task: syncing files at change: 14", run2);
		jenkins.assertLogContains("Found last change 17 on syncID jenkins-NODE_NAME-multiSyncPoll-1", run2);
		jenkins.assertLogContains("Found last change 13 on syncID jenkins-NODE_NAME-multiSyncPoll-2", run2);

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		// Add change to //depot/Data/...
		submitFile(jenkins, "//depot/Data/new01", "Content");

		// Test trigger, build 3
		trigger.poke(job, auth.getP4port());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());

		List<String> log = job.getLastBuild().getLog(1000);
		assertTrue(log.contains("Found last change " + head + " on syncID jenkins-NODE_NAME-multiSyncPoll-1"));
		assertTrue(log.contains("Found last change 14 on syncID jenkins-NODE_NAME-multiSyncPoll-2"));

		// Test trigger, no change
		trigger.poke(job, auth.getP4port());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());
	}
}
