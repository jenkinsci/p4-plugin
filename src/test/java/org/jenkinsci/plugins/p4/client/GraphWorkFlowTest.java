package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.workflow.source.AbstractSource;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

/**
 * Test class for testing org.jenkinsci.plugins.p4.workflow.source.AbstractSource implementation
 */
public class GraphWorkFlowTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-GraphWorkflowTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R17_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "Password", p4d.getRshPort(), CREDENTIAL);
	}

	// Test for syncing a graph source using script with source specified
	@Test
	public void testSyncGraphSources() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', source: [$class: 'GraphSource', graph: '''//graph/docker-plugin/...\n" +
				"//graph/scm-api-plugin/...''']," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "graph-sources-sync");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: //graph/scm-api-plugin.git@81dcf18bca038604c4fc784de42e6069feef8bd1", run);
	}

	@Test
	public void testSyncGraphSource() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', source: [$class: 'GraphSource', graph: '//graph/scm-api-plugin/...']," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "graph-source-sync");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: //graph/scm-api-plugin.git@81dcf18bca038604c4fc784de42e6069feef8bd1", run);
	}

	// Test for syncing a graph source with no source specified. i.e legacy script with new changes.
	@Test
	public void testSyncGraphSourceWithLegacyScript() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', depotPath: '//graph/scm-api-plugin/...'," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "graph-source-sync-legacy");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: //graph/scm-api-plugin.git@81dcf18bca038604c4fc784de42e6069feef8bd1", run);
	}

	// Test for syncing a depot source using script with source specified
	@Test
	public void testSyncDepotSource() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', source: [$class: 'DepotSource', depot: '//depot/...']," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "depot-source-sync");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: 10306", run);
	}

	// Test for syncing a depot source with no source specified. i.e legacy script with new changes.
	@Test
	public void testSyncDepotSourceWithLegacyScript() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', depotPath: '//depot/...'," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "depot-source-sync-legacy");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: 10306", run);
	}

	// Test for syncing a depot source with no source specified. i.e legacy script with new changes.
	@Test
	public void testSyncDepotSourceJava() throws Exception {

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', depotPath: '//graph/scm-api-plugin/....java'," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "depot-java-source-sync-legacy");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: 1030", run);
	}

	@Test
	public void testGetClientView() {
		String clientView = AbstractSource.getClientView("//depot/...", "job1");
		Assert.assertEquals("//depot/... //job1/...", clientView);

		clientView = AbstractSource.getClientView("//depot/*", "job1");
		Assert.assertEquals("//depot/* //job1/*", clientView);

		clientView = AbstractSource.getClientView("//depot/file", "job1");
		Assert.assertEquals("//depot/file //job1/file", clientView);

		clientView = AbstractSource.getClientView("//depot/file space", "job1");
		Assert.assertEquals("\"//depot/file space\" \"//job1/file space\"", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...", "job1");
		Assert.assertEquals("//depot/src/... //job1/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/....java", "job1");
		Assert.assertEquals("//depot/src/....java //job1/....java", clientView);

		clientView = AbstractSource.getClientView("//depot/src/*.java", "job1");
		Assert.assertEquals("//depot/src/*.java //job1/*.java", clientView);

		clientView = AbstractSource.getClientView("//depot/file\n//depot2/file", "job1");
		Assert.assertEquals("//depot/file //job1/depot/file\n//depot2/file //job1/depot2/file", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n//depot/tgt/...", "job1");
		Assert.assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n  //depot/tgt/...", "job1");
		Assert.assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n\t//depot/tgt/...", "job1");
		Assert.assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/....java\n//depot/tgt/....java", "job1");
		Assert.assertEquals("//depot/src/....java //job1/depot/src/....java\n//depot/tgt/....java //job1/depot/tgt/....java", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/", "job1"); //BAD
		Assert.assertEquals("//depot/src //job1/src", clientView);

		// BAD INPUT (four '....' will slip through)
		clientView = AbstractSource.getClientView("//depot/src/....", "job1"); //BAD
		Assert.assertEquals("//depot/src/.... //job1/....", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/\n//depot/tgt", "job1");
		Assert.assertEquals("//depot/src //job1/depot/src\n//depot/tgt //job1/depot/tgt", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/\n//depot/tgt/", "job1");
		Assert.assertEquals("//depot/src //job1/depot/src\n//depot/tgt //job1/depot/tgt", clientView);
	}

	@Test
	public void testParallelSync() throws Exception {

		String client = "jenkins-master-parallelSync-0";

		String pipelineScript = "pipeline{\nagent any \nstages{\nstage('l'){\n" +
				"steps{" +
				"p4sync charset: 'none', credential: '" + CREDENTIAL + "', source: [$class: 'GraphSource', graph: '''//graph/docker-plugin/...\n" +
				"//graph/scm-api-plugin/...''']," +
				"populate: [$class: 'GraphHybridImpl', parallel: [enable: true, minbytes: '2', minfiles: '1', threads: '4'], pin: '', quiet: false]" +
				"\n}\n}\n}\n}";

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "parallelSync");
		job.setDefinition(new CpsFlowDefinition(pipelineScript, false));

		// Run jenkins job.
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertBuildStatusSuccess(run);
		jenkins.assertLogContains("P4 Task: syncing files at change: //graph/scm-api-plugin.git@81dcf18bca038604c4fc784de42e6069feef8bd1", run);

		// Log in for next set of tests...
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		try(ClientHelper p4 = new ClientHelper(job.asItem(), CREDENTIAL, null, workspace)) {
			// Test file exists in workspace root
			String root = p4.getConnection().getClient(client).getRoot();
			assertTrue(Files.exists(Paths.get(root, "graph/scm-api-plugin/README.md")));
		}
	}
}
