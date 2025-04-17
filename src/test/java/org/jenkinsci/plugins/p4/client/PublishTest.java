package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.admin.ITriggerEntry;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.admin.TriggerEntry;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.perforce.p4java.PropertyDefs.IGNORE_FILE_NAME_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PublishTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(FreeStyleTest.class.getName());
	private static final String P4ROOT = "tmp-PublishTest-p4root";
	private static final String SUPER = "super";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
		createCredentials("admin", "Password", p4d.getRshPort(), SUPER);

		System.setProperty(IGNORE_FILE_NAME_KEY, "other");
	}

	@Test
	public void testPublishWithPurge() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-purge");

		// Create workspace
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// Populate with P4 scm
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact("artifact.1", "content"));
		project.getBuildersList().add(new CreateArtifact("artifact.2", "content"));

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, false, true, "3");
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Stat file and check type
		ClientHelper p4 = new ClientHelper(project, CREDENTIAL, null, workspace);
		List<IFileSpec> fileSpec = FileSpecBuilder.makeFileSpecList("//depot/Data/artifact.1");
		GetExtendedFilesOptions opts = new GetExtendedFilesOptions();
		List<IExtendedFileSpec> eSpec = p4.getConnection().getExtendedFiles(fileSpec, opts);
		assertEquals("text+S3", eSpec.get(0).getHeadType());
	}

	@Test
	public void testPublishWithFilter() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-filter");

		// Create workspace
		String client = "manual.ws";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// Populate with P4 scm
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact("file.1", "content"));
		project.getBuildersList().add(new CreateArtifact("file.2", "content"));

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, false, true, null);
		submit.setPaths("//depot/Data/none/...\n  file.1");
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Stat file and check type
		ClientHelper p4 = new ClientHelper(project, CREDENTIAL, null, workspace);
		List<IFileSpec> fileSpec = FileSpecBuilder.makeFileSpecList("//depot/Data/file.*");
		GetExtendedFilesOptions opts = new GetExtendedFilesOptions();
		List<IExtendedFileSpec> eSpec = p4.getConnection().getExtendedFiles(fileSpec, opts);

		assertNotNull(eSpec);
		assertEquals(1, eSpec.size());
		assertEquals("//depot/Data/file.1", eSpec.get(0).getDepotPathString());
	}

	@Test
	public void testPublishWithFail() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-fail");

		// Create workspace
		String client = "manual-publish-fail.ws";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// Populate with P4 scm
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact("artifact.1", "content"));
		project.getBuildersList().add(new CreateArtifact("artifact.2", "content"));

		// Add trigger to fail submit
		ClientHelper p4 = new ClientHelper(project, SUPER, null, workspace);
		List<ITriggerEntry> triggers = new ArrayList<>();
		ITriggerEntry entry1 = new TriggerEntry(0, "fail",
				ITriggerEntry.TriggerType.CHANGE_SUBMIT,
				"//...", "\"exit 1\""
		);
		triggers.add(entry1);
		String message = p4.getConnection().createTriggerEntries(triggers);
		assertNotNull(message);
		assertEquals("Triggers saved.", message);

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, false, true, "3");
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.FAILURE, build.getResult());
		jenkins.assertLogContains("'fail' validation failed: ", build);
	}

	@Test
	public void testHighAsciiDescriptions() throws Exception {

		byte[] byteArray = new byte[]{'t', 'e', 's', 't', 5, '.'};
		String desc = new String(byteArray, StandardCharsets.UTF_8);
		submitFile(jenkins, "//depot/classic/A/src/fileA", "content", desc);


		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "highAscii");
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
		assertEquals(Result.SUCCESS, run.getResult());

		P4ChangeSet changeSet = (P4ChangeSet) run.getChangeSets().get(0);
		String msg = changeSet.getHistory().get(0).getMsg();
		assertEquals("test?.", msg);
	}
	@Test
	public void publishShouldCleanTheP4ClientWhenManualSpecCleanupIsTrue() throws Exception {
		WorkflowJob project = jenkins.jenkins.createProject(WorkflowJob.class, "Publish-Cleanup");

		project.setDefinition(new CpsFlowDefinition(" " +
				"pipeline { \n" +
				"  agent any\n" +
				"  options { skipDefaultCheckout() } \n" +
				"  stages { " +
				"    stage (\"Submit\") { " +
				"      steps { " +
				"        script { " +
				"          writeFile file: 'Test.log', text: \"${BUILD_NUMBER}\" \n" +
				"          p4publish credential: '" + CREDENTIAL + "', " +
				"          publish: submit(description: 'Submitted by Jenkins. Build: ${BUILD_TAG}'), " +
				"          workspace: " +
				"            manualSpec(" +
				"              charset: 'none', cleanup: true," +
				"              name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}-submit', " +
				"              spec: " +
				"                clientSpec(" +
				"                  clobber: true, line: 'LOCAL',type: 'WRITABLE', " +
				"            	   view: '//depot/cleanup/... //jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}-submit/...'" +
				"                )" +
				"            ) " +
				"        } " +
				"      } " +
				"    } " +
				"  } " +
				"}", false));

		WorkflowRun run = jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: cleanup client:", run);
		jenkins.assertLogContains("Client jenkins-master-Publish-Cleanup-0-submit deleted.", run);
	}

}
