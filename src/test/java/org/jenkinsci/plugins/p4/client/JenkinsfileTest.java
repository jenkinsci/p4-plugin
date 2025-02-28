package org.jenkinsci.plugins.p4.client;

import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.ExtendedJenkinsRule;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JenkinsfileTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(JenkinsfileTest.class.getName());
	private static final String P4ROOT = "tmp-JenkinsfileTest-p4root";

	@ClassRule
	public static ExtendedJenkinsRule jenkins = new ExtendedJenkinsRule(7 * 60);

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
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
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "basicJenkinsfile");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Get current change
		ClientHelper p4 = new ClientHelper(job, CREDENTIAL, null, workspace);
		int head = Integer.parseInt(p4.getCounter("change"));

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
		trigger.poke(job, p4d.getRshPort());

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
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "diffClientsJenkinsfile");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// create pseudo environment (normally ClientHelper is called from Run)
		EnvVars envVars = new EnvVars();
		envVars.put("NODE_NAME", "NODE_NAME");
		envVars.put("JOB_NAME", "JOB_NAME");
		workspace.setExpand(envVars);

		// Get current change
		ClientHelper p4 = new ClientHelper(job, CREDENTIAL, null, workspace);
		int head = Integer.parseInt(p4.getCounter("change"));

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
		trigger.poke(job, p4d.getRshPort());

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
				+ "      depotPath: '//depot/Data/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '17', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '13', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content1);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

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
				+ "      depotPath: '//depot/Data/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '18', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main/...',\n"
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
				+ "      depotPath: '//depot/Data/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '17', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '13', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content1);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

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
				+ "      depotPath: '//depot/Data/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '', quiet: true]\n"
				+ "   p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2',\n"
				+ "      depotPath: '//depot/Main/...',\n"
				+ "      populate: [$class: 'AutoCleanImpl', pin: '14', quiet: true]\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content2);

		// create pseudo environment (normally ClientHelper is called from Run)
		EnvVars envVars = new EnvVars();
		envVars.put("NODE_NAME", "NODE_NAME");
		envVars.put("JOB_NAME", "JOB_NAME");
		workspace.setExpand(envVars);

		// Get latest change
		ClientHelper p4 = new ClientHelper(job, CREDENTIAL, null, workspace);
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
		trigger.poke(job, p4d.getRshPort());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());

		List<String> log = job.getLastBuild().getLog(1000);
		assertTrue(log.contains("Found last change " + head + " on syncID jenkins-NODE_NAME-multiSyncPoll-1"));
		assertTrue(log.contains("Found last change 14 on syncID jenkins-NODE_NAME-multiSyncPoll-2"));

		// Test trigger, no change
		trigger.poke(job, p4d.getRshPort());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());
	}

	@Test
	@Issue("JENKINS-43770")
	public void testMultiSyncParallelPolling() throws Exception {

		String content1 = ""
				+ "parallel first: {\n"
				+ "    node {\n"
				+ "        p4sync credential: '" + CREDENTIAL + "',\n"
				+ "           format: 'jenkins-master-${JOB_NAME}-1',\n"
				+ "           depotPath: '//depot/Data/...',\n"
				+ "           populate: [$class: 'AutoCleanImpl', pin: '1', quiet: true]\n"
				+ "    }\n"
				+ "},\n"
				+ "second: {\n"
				+ "    node {\n"
				+ "        p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "           format: 'jenkins-master-${JOB_NAME}-2',\n"
				+ "           depotPath: '//depot/Main/...',\n"
				+ "           populate: [$class: 'AutoCleanImpl', pin: '8', quiet: true]\n"
				+ "    }\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content1);

		// Manual workspace spec definition
		String client = "jenkins-master-${JOB_NAME}-script";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/Jenkinsfile //" + client + "/Jenkinsfile";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "multiParallelSyncPoll");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Build 1
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run1);
		assertEquals(1, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: 1", run1);
		jenkins.assertLogContains("P4 Task: syncing files at change: 8", run1);

		String content2 = ""
				+ "parallel first_branch: {\n"
				+ "    node {\n"
				+ "        p4sync credential: '" + CREDENTIAL + "',\n"
				+ "           format: 'jenkins-master-${JOB_NAME}-1',\n"
				+ "           depotPath: '//depot/Data/...',\n"
				+ "           populate: [$class: 'AutoCleanImpl', pin: '', quiet: true]\n"
				+ "    }\n"
				+ "},\n"
				+ "second_branch: {\n"
				+ "    node {\n"
				+ "        p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n"
				+ "           format: 'jenkins-master-${JOB_NAME}-2',\n"
				+ "           depotPath: '//depot/Main/...',\n"
				+ "           populate: [$class: 'AutoCleanImpl', pin: '9', quiet: true]\n"
				+ "    }\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content2);

		// create pseudo environment (normally ClientHelper is called from Run)
		EnvVars envVars = new EnvVars();
		envVars.put("JOB_NAME", "JOB_NAME");
		workspace.setExpand(envVars);

		// Get latest change
		ClientHelper p4 = new ClientHelper(job, CREDENTIAL, null, workspace);
		int head = Integer.parseInt(p4.getCounter("change"));

		// Build 2
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run2);
		assertEquals(2, job.getLastBuild().getNumber());
		jenkins.assertLogContains("P4 Task: syncing files at change: " + head, run2);
		jenkins.assertLogContains("P4 Task: syncing files at change: 9", run2);
		jenkins.assertLogContains("Found last change 1 on syncID jenkins-master-multiParallelSyncPoll-1", run2);
		jenkins.assertLogContains("Found last change 8 on syncID jenkins-master-multiParallelSyncPoll-2", run2);

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		// Add change to //depot/Data/...
		submitFile(jenkins, "//depot/Data/new01", "Content");

		// Test trigger, build 3
		trigger.poke(job, p4d.getRshPort());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());

		List<String> log = job.getLastBuild().getLog(1000);
		assertTrue(log.contains("Found last change " + head + " on syncID jenkins-master-multiParallelSyncPoll-1"));
		assertTrue(log.contains("Found last change 9 on syncID jenkins-master-multiParallelSyncPoll-2"));

		// Test trigger, no change
		trigger.poke(job, p4d.getRshPort());
		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();
		assertEquals(3, job.getLastBuild().getNumber());
	}

	@Test
	public void testJenkinsfileLocation() throws Exception {

		String content = """
				\
				node {
				   echo 'Alt Jenkinsfile'
				   println "P4_CHANGELIST: ${env.P4_CHANGELIST}"
				}""";

		submitFile(jenkins, "//depot/Other/Jenkinsfile", content);
		String change = submitFile(jenkins, "//depot/Data/j001", "Content");

		// Manual workspace spec definition
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		StringBuilder sb = new StringBuilder();
		sb.append("//depot/Data/... //" + client + "/..." + "\n");
		sb.append("//depot/Other/Jenkinsfile //" + client + "/build/Jenkinsfile");
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, sb.toString(), null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "jenkinsfileLocation");
		job.setDefinition(new CpsScmFlowDefinition(scm, "build/Jenkinsfile"));

		// Build 1
		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("Alt Jenkinsfile", run);
		jenkins.assertLogContains("P4_CHANGELIST: " + change, run);
	}

	@Test
	public void testJenkinsfileLocationLightweight() throws Exception {

		String content = """
				\
				node {
				   echo 'Alt Jenkinsfile'
				   println "P4_CHANGELIST: ${env.P4_CHANGELIST}"
				}""";

		submitFile(jenkins, "//depot/Other/Jenkinsfile", content);
		String change = submitFile(jenkins, "//depot/Data/j002", "Content");

		// Manual workspace spec definition
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		StringBuilder sb = new StringBuilder();
		sb.append("//depot/Data/... //" + client + "/..." + "\n");
		sb.append("//depot/Other/Jenkinsfile //" + client + "/build/Jenkinsfile");
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, sb.toString(), null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "jenkinsfileLocationLightweight");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "build/Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// Get current change
		ClientHelper p4 = new ClientHelper(job, CREDENTIAL, null, workspace);
		String head = p4.getCounter("change");

		// Build 1
		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("Alt Jenkinsfile", run);
		assertEquals(head, change);
	}

	@Test
	public void testPipelineJenkinsfilePathEnvVar() throws Exception {
		String base = "//depot/envJfile";
		String scriptPath = "Jenkinsfile";
		submitFile(jenkins, base + "/" + scriptPath, """
				\
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        script {
				             echo "The jenkinsfile path is: ${JENKINSFILE_PATH}"\
				        }
				      }
				    }
				  }
				}""");

		// Manual workspace spec definition
		String client = "envJfile.ws";
		String view = base + "/" + "..." + " //" + client + "/" + "...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job LightWeight Checkout
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "pipelineJenkinsfilePathEnvVar");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, scriptPath);
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// Build 1
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run1);
		jenkins.assertLogContains("The jenkinsfile path is: " + base + "/" + scriptPath, run1);

		// SCM Jenkinsfile job Full Checkout
		cpsScmFlowDefinition.setLightweight(false);
		job.setDefinition(cpsScmFlowDefinition);

		// Build 2
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run2);
		jenkins.assertLogContains("The jenkinsfile path is: " + base + "/" + scriptPath, run2);
	}

	@Test
	public void testNodeJenkinsfilePathEnvVar() throws Exception {
		String base = "//depot/envJfile";
		String scriptPath = "Jenkinsfile";
		submitFile(jenkins, base + "/" + scriptPath, ""
				+ "node() {\n"
				+ "  echo \"The jenkinsfile path is: ${JENKINSFILE_PATH}\""
				+ "}");

		// Manual workspace spec definition
		String client = "envJfile.ws";
		String view = base + "/" + "..." + " //" + client + "/" + "...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job Full Checkout
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "nodeJenkinsfilePathEnvVar");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, scriptPath);
		cpsScmFlowDefinition.setLightweight(false);
		job.setDefinition(cpsScmFlowDefinition);

		// Build 1
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run1);
		jenkins.assertLogContains("The jenkinsfile path is: " + base + "/" + scriptPath, run1);

	/*
	    // SCM Jenkinsfile job LightWeight Checkout
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// Build 2
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		jenkins.assertBuildStatusSuccess(run2);
		jenkins.assertLogContains("The jenkinsfile path is: " + base + "/" + scriptPath, run2);
	*/
	}


	@Test
	public void testParametersStreamJenkinsfile() throws Exception {

		// Create workspace
		submitStreamFile(jenkins, "//stream/main/Jenkinsfile", "node() {}", "desc");

		// Stream Spec
		String client = "streamTest.ws";
		String stream = "//stream/${ST}";
		StreamWorkspaceImpl workspace = new StreamWorkspaceImpl("none", false, stream, client);
		workspace.setExpand(new HashMap<>());
		File wsRoot = new File("target/manualStream.ws").getAbsoluteFile();
		workspace.setRootPath(wsRoot.toString());

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job Full Checkout
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "testParametersStreamJenkinsfile");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(false);
		job.setDefinition(cpsScmFlowDefinition);

		// Add Parameters to build
		StringParameterDefinition spd = new StringParameterDefinition("ST", "default");
		ParametersDefinitionProperty def = new ParametersDefinitionProperty(spd);
		job.addProperty(def);

		// Build 1 with ST=main
		ParameterValue param = new StringParameterValue("ST", "main");
		Action actions = new ParametersAction(param);
		WorkflowRun run1 = job.scheduleBuild2(0, actions).get();
		jenkins.assertBuildStatusSuccess(run1);
	}
}
