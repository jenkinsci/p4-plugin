package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IMapEntry;
import com.perforce.p4java.impl.generic.client.ClientView;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.scm.GlobalLibraryScmSource;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
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

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testWorkflow() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'\n"
				+ "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'\n"
				+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]\n"
				+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']\n"
				+ "   p4publish credential: '" + CREDENTIAL + "', publish: publisher, workspace: buildWorkspace" + " \n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.", run);
	}

	@Test
	public void testWorkflowEnv() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "workflowEnv");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4_CHANGELIST: 40", run);
	}

	@Test
	public void testManualP4Sync() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "manualP4Sync");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'org.jenkinsci.plugins.p4.populate.SyncOnlyImpl',\n"
				+ "      revert:true, have:true, modtime:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + CREDENTIAL + "', populate: syncOptions\n"
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
				+ "   p4 = p4(credential: '" + CREDENTIAL + "', workspace: ws)\n"
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
				+ "   p4 = p4(credential: '" + CREDENTIAL + "', workspace: ws)\n"
				+ "   clientName = p4.getClientName();\n"
				+ "   client = p4.fetch('client', clientName)\n"
				+ "   echo \"Client: $client\"\n"
				+ "   client.put('Description', 'foo')\n"
				+ "   p4.save('client', client)\n"
				+ "}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("p4 client -o jenkins-master-p4groovy.spec", run);
	}

	@Test
	public void testP4GroovyForceSpecEdit() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p4groovy.spec.force");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n"
				+ "   ws = [$class: 'StreamWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//stream/main']\n"
				+ "   p4 = p4(credential: '" + CREDENTIAL + "', workspace: ws)\n"
				+ "   clientName = p4.getClientName();\n"
				+ "   client = p4.fetch('client', clientName)\n"
				+ "   echo \"Client: $client\"\n"
				+ "   client.put('Description', 'foo')\n"
				+ "   res = p4.save('client', client, '-f')\n"
				+ "   echo \"Result: $res\"\n"
				+ "}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();

		// Look for no permission as '-f' flag requires 'admin' permissions
		jenkins.assertLogContains("You don't have permission for this operation.", run);
	}

	@Test
	public void testSyncIDManualP4Sync() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "syncIDmanualP4Sync");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      syncID: 'foo-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'org.jenkinsci.plugins.p4.populate.SyncOnlyImpl',\n"
				+ "      revert:true, have:true, modtime:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + CREDENTIAL + "', populate: syncOptions\n"
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
				+ "   p4 = p4(credential: '" + CREDENTIAL + "', workspace: ws)\n"
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
				"           credential: '" + CREDENTIAL + "', " +
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
				"      credential: '" + CREDENTIAL + "', \n" +
				"      format: 'jenkins-${NODE_NAME}-${JOB_NAME}-1', \n" +
				"      populate: forceClean(quiet: true), \n" +
				"      source: streamSource('//stream/main')\n" +
				"\n" +
				"    p4cleanup(true)\n" +
				"\n" +
				"    p4sync charset: 'none', \n" +
				"      credential: '" + CREDENTIAL + "', \n" +
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

		// change in source (after library)
		submitFile(jenkins, "//depot/Data/fileA", content);

		// configure Global Library
		String path = "//depot/library/...";
		GlobalLibraryScmSource scm = new GlobalLibraryScmSource(CREDENTIAL, null, path);
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
				"      credential: '" + CREDENTIAL + "', \n" +
				"      populate: autoClean(quiet: true), \n" +
				"      source: depotSource('//depot/Data/...')\n" +
				"    sayHello 'Jenkins'\n" +
				"    println \"SYNC_CHANGELIST: ${env.P4_CHANGELIST}\"\n" +
				"}", false));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, run.getResult());
		jenkins.assertLogContains("SYNC_CHANGELIST: 45", run);

		// Clear Global Libraries for other Jobs
		globalLib.setLibraries(new ArrayList<LibraryConfiguration>());
	}

	@Test
	public void testPreviewCheckout() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "previewCheckout");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'CheckOnlyImpl', quiet:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + CREDENTIAL + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("p4 sync -n -q", run);
	}

	@Test
	public void testFlushCheckout() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "flushCheckout");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   def workspace = [$class: 'ManualWorkspaceImpl',\n"
				+ "      name: 'jenkins-${NODE_NAME}-${JOB_NAME}',\n"
				+ "      spec: [view: '//depot/... //jenkins-${NODE_NAME}-${JOB_NAME}/...']]\n"
				+ "   def syncOptions = [$class: 'FlushOnlyImpl', quiet:true]\n"
				+ "   p4sync workspace:workspace, credential: '" + CREDENTIAL + "', populate: syncOptions\n"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("p4 sync -k -q", run);
	}

	@Test
	public void testP4SyncEllipsisAndDot() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "workflowEllipsis");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', "
				+ "      source: depotSource('''//depot/test/projA/1.1.0/...\n//depot/test/projA/1.2.0/...''')"
				+ "}", false));
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		String name = "jenkins-master-workflowEllipsis-0";
		Workspace workspace = defaultWorkspace(name);
		ClientHelper p4 = new ClientHelper(job.asItem(), CREDENTIAL, null, workspace);
		p4.login();

		IClient client = p4.getConnection().getCurrentClient();
		assertNotNull(client);

		ClientView view = client.getClientView();
		assertNotNull(view);

		assertEquals("//jenkins-master-workflowEllipsis-0/depot/test/projA/1.1.0/...", view.getEntry(0).getRight());
		assertEquals("//jenkins-master-workflowEllipsis-0/depot/test/projA/1.2.0/...", view.getEntry(1).getRight());

		p4.disconnect();
	}

	@Test
	public void testP4SyncSpaceInPath() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "workflowSpace");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', "
				+ "      source: depotSource('''//depot/test/proj A/...\n//depot/test/proj B/...''')"
				+ "}", false));
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		String name = "jenkins-master-workflowSpace-0";
		Workspace workspace = defaultWorkspace(name);
		ClientHelper p4 = new ClientHelper(job.asItem(), CREDENTIAL, null, workspace);
		p4.login();

		IClient client = p4.getConnection().getCurrentClient();
		assertNotNull(client);

		ClientView view = client.getClientView();
		assertNotNull(view);

		assertEquals("//jenkins-master-workflowSpace-0/depot/test/proj A/...", view.getEntry(0).getRight());
		assertEquals("//jenkins-master-workflowSpace-0/depot/test/proj B/...", view.getEntry(1).getRight());

		p4.disconnect();
	}

	@Test
	public void testP4SyncFileOnly() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "workflowFile");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', "
				+ "      source: depotSource('''//depot/Main/file-4.txt\n//depot/Main/file-5.txt''')"
				+ "}", false));
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		String name = "jenkins-master-workflowFile-0";
		Workspace workspace = defaultWorkspace(name);
		ClientHelper p4 = new ClientHelper(job.asItem(), CREDENTIAL, null, workspace);
		p4.login();

		IClient client = p4.getConnection().getCurrentClient();
		assertNotNull(client);

		ClientView view = client.getClientView();
		assertNotNull(view);

		assertEquals("//jenkins-master-workflowFile-0/depot/Main/file-4.txt", view.getEntry(0).getRight());
		assertEquals("//jenkins-master-workflowFile-0/depot/Main/file-5.txt", view.getEntry(1).getRight());

		p4.disconnect();
	}

	@Test
	public void testExcludeDepotSourcePath() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "excludeDepotSource");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', "
				+ "      source: depotSource('''//depot/Main/file-4.txt\n-//depot/Main/file-5.txt''')"
				+ "}", false));
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		String name = "jenkins-master-excludeDepotSource-0";
		Workspace workspace = defaultWorkspace(name);
		ClientHelper p4 = new ClientHelper(job.asItem(), CREDENTIAL, null, workspace);
		p4.login();

		IClient client = p4.getConnection().getCurrentClient();
		assertNotNull(client);

		ClientView view = client.getClientView();
		assertNotNull(view);

		assertEquals("//depot/Main/file-4.txt", view.getEntry(0).getLeft());
		assertEquals("//depot/Main/file-5.txt", view.getEntry(1).getLeft());
		assertEquals(IMapEntry.EntryType.EXCLUDE, view.getEntry(1).getType());

		p4.disconnect();
	}
}
