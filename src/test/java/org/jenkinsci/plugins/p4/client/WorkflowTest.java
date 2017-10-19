package org.jenkinsci.plugins.p4.client;

import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.scm.GlobalLibraryScmSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WorkflowTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-WorkflowTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
	}

	@Test
	public void testWorkflow() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + id + "', template: 'test.ws'\n"
				+ "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'\n"
				+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]\n"
				+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']\n"
				+ "   p4publish credential: '" + id + "', publish: publisher, workspace: buildWorkspace" + " \n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.", run);
	}

	@Test
	public void testWorkflowEnv() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "workflowEnv");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + id + "', template: 'test.ws'\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4_CHANGELIST: 40", run);
	}

	@Test
	public void testManualP4Sync() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "manualP4Sync");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'org.jenkinsci.plugins.p4.populate.SyncOnlyImpl',\n"
				+ "      revert:true, have:true, modtime:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + id + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
	}

	@Test
	public void testP4GroovyConnectAndSync() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p4groovy");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n"
				+ "   ws = [$class: 'StreamWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//stream/main']\n"
				+ "   p4 = p4(credential: '" + auth.getId() + "', workspace: ws)\n"
				+ "   p4.run('sync', '//...')\n"
				+ "}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("p4 sync //...", run);
		jenkins.assertLogContains("totalFileCount 10", run);
	}

	@Test
	public void testP4GroovySpecEdit() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p4groovy.spec");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n"
				+ "   ws = [$class: 'StreamWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//stream/main']\n"
				+ "   p4 = p4(credential: '" + auth.getId() + "', workspace: ws)\n"
				+ "   clientName = p4.getClientName();\n"
				+ "   client = p4.fetch('client', clientName)\n"
				+ "   echo \"Client: ${client}\""
				+ "   client.put('Description', 'foo')"
				+ "   p4.save('client', client)"
				+ "}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("p4 client -o jenkins-master-p4groovy.spec", run);
	}

	@Test
	public void testSyncIDManualP4Sync() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "syncIDmanualP4Sync");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      syncID: 'foo-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'org.jenkinsci.plugins.p4.populate.SyncOnlyImpl',\n"
				+ "      revert:true, have:true, modtime:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + id + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);

		WorkflowRun run2 = job.scheduleBuild2(job.getQuietPeriod()).get();
		jenkins.assertBuildStatusSuccess(run2);
		assertEquals(2, job.getLastBuild().getNumber());
		jenkins.assertLogContains("Found last change 40 on syncID foo-NODE_NAME-syncIDmanualP4Sync", run2);
	}

	@Test
	public void testP4GroovyMultiArg() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "multiArg");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n"
				+ "   ws = [$class: 'StreamWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//stream/main']\n"
				+ "   p4 = p4(credential: '" + auth.getId() + "', workspace: ws)\n"
				+ "   p4.run('sync', '//...')\n"
				+ "   p4.run('changes', '-m4', '//...@24,27')\n"
				+ "}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("Change 27", run);
		jenkins.assertLogContains("Change 24", run);
		jenkins.assertLogNotContains("Change 1", run);
		jenkins.assertLogNotContains("Change 40", run);
	}

	@Test
	public void testCheckoutEnvironment() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "checkoutEnv");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n" +
				"    stage('Sync files...') {\n" +
				"        checkout([$class: 'PerforceScm', " +
				"           credential: '" + auth.getId() + "', " +
				"           populate: [$class: 'ForceCleanImpl', have: false, pin: '', quiet: true], " +
				"           workspace: [$class: 'StreamWorkspaceImpl', charset: 'none', " +
				"              format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, " +
				"              streamName: '//stream/main']])\n" +
				"    }\n" +
				"    stage ('Test env...') {\n" +
				"        println \"P4_CHANGELIST=${env.P4_CHANGELIST}\"\n" +
				"        println \"P4_CLIENT=${env.P4_CLIENT}\"\n" +
				"        println \"HUDSON_CHANGELOG_FILE=${env.HUDSON_CHANGELOG_FILE}\"\n" +
				"        println \"P4_USER=${env.P4_USER}\"\n" +
				"        println \"P4_TICKET=${env.P4_TICKET}\"\n" +
				"    }\n" +
				"}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("P4_CHANGELIST=39", run);
		jenkins.assertLogContains("P4_CLIENT=jenkins-master-checkoutEnv", run);
		jenkins.assertLogContains("P4_USER=jenkins", run);
		jenkins.assertLogNotContains("HUDSON_CHANGELOG_FILE=null", run);
	}

	@Test
	public void testCleanupClient() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "cleanup");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node () {\n" +
				"    p4sync charset: 'none', \n" +
				"      credential: '" + auth.getId() + "', \n" +
				"      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1', \n" +
				"      populate: forceClean(quiet: true), \n" +
				"      source: streamSource('//stream/main')\n" +
				"\n" +
				"    p4cleanup(true)\n" +
				"\n" +
				"    p4sync charset: 'none', \n" +
				"      credential: '" + auth.getId() + "', \n" +
				"      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-2', \n" +
				"      populate: forceClean(quiet: true), \n" +
				"      source: streamSource('//stream/main')\n" +
				"\n" +
				"    p4cleanup(true)\n" +
				"}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("P4 Task: cleanup client: jenkins-master-cleanup-1", run);
		jenkins.assertLogContains("P4 Task: cleanup client: jenkins-master-cleanup-2", run);
	}

	@Test
	public void testGlobalLib() throws Exception {

		// submit test library
		String content = ""
				+ "def call(String name = 'human') {\n" +
				"    echo \"Hello again, ${name}.\"\n" +
				"}";
		submitFile(jenkins, "//depot/library/vars/sayHello.groovy", content);

		// configure Global Library
		String path = "//depot/library";
		GlobalLibraryScmSource scm = new GlobalLibraryScmSource("p4", auth.getId(), null, path);
		SCMSourceRetriever source = new SCMSourceRetriever(scm);
		LibraryConfiguration config = new LibraryConfiguration("testLib", source);
		config.setImplicit(true);
		config.setDefaultVersion("now");

		GlobalLibraries globalLib = (GlobalLibraries) jenkins.getInstance().getDescriptor(GlobalLibraries.class);
		assertNotNull(globalLib);
		globalLib.setLibraries(Arrays.asList(config));

		// create job using library
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "useLib");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n" +
				"    p4sync charset: 'none', \n" +
				"      credential: '" + auth.getId() + "', \n" +
				"      populate: autoClean(quiet: true), \n" +
				"      source: streamSource('//stream/main')\n" +
				"    sayHello 'Jenkins'\n" +
				"}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, run.getResult());
		jenkins.assertLogContains("Hello again, Jenkins.", run);

		// Clear Global Libraries for other Jobs
		globalLib.setLibraries(new ArrayList<LibraryConfiguration>());
	}

	@Test
	public void testPreviewCheckout() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "previewCheckout");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'CheckOnlyImpl', quiet:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + id + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("p4 sync -n -q", run);
	}

	@Test
	public void testFlushCheckout() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "flushCheckout");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'FlushOnlyImpl', quiet:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + id + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("p4 sync -k -q", run);
	}
}
