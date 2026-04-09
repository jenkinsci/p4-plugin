package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.workflow.source.AbstractSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for testing org.jenkinsci.plugins.p4.workflow.source.AbstractSource implementation
 */
@WithJenkins
class GraphWorkFlowTest extends DefaultEnvironment {

	private static final Logger LOGGER = Logger.getLogger(GraphWorkFlowTest.class.getName());
	private static final String P4ROOT = "tmp-GraphWorkflowTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r17);

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
    }

    @BeforeEach
    void beforeEach() throws Exception {
		createCredentials("jenkins", "Password", p4d.getRshPort(), CREDENTIAL);
	}

	// Test for syncing a graph source using script with source specified
	@Disabled
	@Test
	void testSyncGraphSources() throws Exception {
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

	@Disabled
	@Test
	void testSyncGraphSource() throws Exception {
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
	@Disabled
	@Test
	void testSyncGraphSourceWithLegacyScript() throws Exception {
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
	void testSyncDepotSource() throws Exception {
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
	void testSyncDepotSourceWithLegacyScript() throws Exception {
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
	@Disabled
	@Test
	void testSyncDepotSourceJava() throws Exception {
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
	void testGetClientView() {
		String clientView = AbstractSource.getClientView("//depot/...", "job1");
		assertEquals("//depot/... //job1/...", clientView);

		clientView = AbstractSource.getClientView("//depot/*", "job1");
		assertEquals("//depot/* //job1/*", clientView);

		clientView = AbstractSource.getClientView("//depot/file", "job1");
		assertEquals("//depot/file //job1/file", clientView);

		clientView = AbstractSource.getClientView("//depot/file space", "job1");
		assertEquals("\"//depot/file space\" \"//job1/file space\"", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...", "job1");
		assertEquals("//depot/src/... //job1/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/....java", "job1");
		assertEquals("//depot/src/....java //job1/....java", clientView);

		clientView = AbstractSource.getClientView("//depot/src/*.java", "job1");
		assertEquals("//depot/src/*.java //job1/*.java", clientView);

		clientView = AbstractSource.getClientView("//depot/file\n//depot2/file", "job1");
		assertEquals("//depot/file //job1/depot/file\n//depot2/file //job1/depot2/file", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n//depot/tgt/...", "job1");
		assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n  //depot/tgt/...", "job1");
		assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/...\n\t//depot/tgt/...", "job1");
		assertEquals("//depot/src/... //job1/depot/src/...\n//depot/tgt/... //job1/depot/tgt/...", clientView);

		clientView = AbstractSource.getClientView("//depot/src/....java\n//depot/tgt/....java", "job1");
		assertEquals("//depot/src/....java //job1/depot/src/....java\n//depot/tgt/....java //job1/depot/tgt/....java", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/", "job1"); //BAD
		assertEquals("//depot/src //job1/src", clientView);

		// BAD INPUT (four '....' will slip through)
		clientView = AbstractSource.getClientView("//depot/src/....", "job1"); //BAD
		assertEquals("//depot/src/.... //job1/....", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/\n//depot/tgt", "job1");
		assertEquals("//depot/src //job1/depot/src\n//depot/tgt //job1/depot/tgt", clientView);

		// BAD INPUT (treated as file)
		clientView = AbstractSource.getClientView("//depot/src/\n//depot/tgt/", "job1");
		assertEquals("//depot/src //job1/depot/src\n//depot/tgt //job1/depot/tgt", clientView);
	}

	@Disabled
	@Test
	void testParallelSync() throws Exception {
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
		try(ConnectionHelper p4 = new ConnectionHelper(job.asItem(), CREDENTIAL, null)) {
			// Test file exists in workspace root
			String root = p4.getConnection().getClient(client).getRoot();
			assertTrue(Files.exists(Paths.get(root, "graph/scm-api-plugin/README.md")));
		}
	}
}
