package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class WorkflowTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-WorkflowTest-p4root";
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
				+ "}"));
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
				+ "}"));
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
				+ "}"));
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
				+ "}"));
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
				+ "}"));
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
				+ "}"));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);

		WorkflowRun run2 = job.scheduleBuild2(job.getQuietPeriod()).get();
		jenkins.assertBuildStatusSuccess(run2);
		assertEquals(2, job.getLastBuild().getNumber());
		jenkins.assertLogContains("Found last change 40 on syncID foo-NODE_NAME-syncIDmanualP4Sync", run2);
	}
}
