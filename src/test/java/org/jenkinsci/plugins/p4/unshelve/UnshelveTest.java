package org.jenkinsci.plugins.p4.unshelve;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.option.server.OpenedFilesOptions;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.ListBoxModel;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class UnshelveTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-UnshelveTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeAll
	static void beforeAll(JenkinsRule rule) {
		jenkins = rule;
	}

	@BeforeEach
	void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	private PerforceScm scmForView(String client, String view) {
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		Populate populate = new AutoCleanImpl();
		return new PerforceScm(CREDENTIAL, workspace, populate);
	}

	@Test
	void unshelveAppliesShelvedFileOnTopOfSyncedWorkspace() throws Exception {
		submitFile(jenkins, "//depot/UnshelveBuilderTest/sync/base.txt", "base");
		String shelveId = shelveFile(jenkins, "//depot/UnshelveBuilderTest/apply/file.txt", "shelved-content");

		String client = "unshelve-apply.ws";
		PerforceScm scm = scmForView(client, "//depot/UnshelveBuilderTest/... //" + client + "/...");

		FreeStyleProject project = jenkins.createFreeStyleProject("UnshelveApply");
		project.setScm(scm);
		project.getBuildersList().add(new UnshelveBuilder(shelveId, "at", false, false));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: " + shelveId));

		FilePath applied = build.getWorkspace().child("apply/file.txt");
		assertTrue(applied.exists(), "shelved file should have been applied to the build workspace");
		assertEquals("shelved-content", applied.readToString());

		HtmlPage page = jenkins.createWebClient().getPage(build, "changes");
		String text = page.asNormalizedText();
		assertTrue(text.contains("Shelved Files:"));
		assertTrue(text.contains("//depot/UnshelveBuilderTest/apply/file.txt"));
	}

	@Test
	void tidyRevertsOpenedFilesWithoutDiscardingUnshelvedContent() throws Exception {
		submitFile(jenkins, "//depot/UnshelveBuilderTest/tidy/sync/base.txt", "base");
		String shelveId = shelveFile(jenkins, "//depot/UnshelveBuilderTest/tidy/apply/file.txt", "shelved-content");

		String client = "unshelve-tidy.ws";
		PerforceScm scm = scmForView(client, "//depot/UnshelveBuilderTest/tidy/... //" + client + "/...");

		FreeStyleProject project = jenkins.createFreeStyleProject("UnshelveTidy");
		project.setScm(scm);
		project.getBuildersList().add(new UnshelveBuilder(shelveId, "at", true, false));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());

		FilePath applied = build.getWorkspace().child("apply/file.txt");
		assertTrue(applied.exists(), "tidy should keep the unshelved content on disk");
		assertEquals("shelved-content", applied.readToString());

		Workspace workspace = scm.getWorkspace();
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			List<IFileSpec> opened = p4.getClient().openedFiles(null, new OpenedFilesOptions());
			assertTrue(opened.isEmpty(), "tidy=true should revert the unshelved file out of the opened/pending state");
		}
	}

	@Test
	void ignoreEmptySkipsUnshelveWhenShelfResolvesToBlank() throws Exception {
		String client = "unshelve-skip.ws";
		PerforceScm scm = scmForView(client, "//depot/UnshelveBuilderTest/... //" + client + "/...");

		FreeStyleProject project = jenkins.createFreeStyleProject("UnshelveIgnoreEmpty");
		project.setScm(scm);
		project.getBuildersList().add(new UnshelveBuilder("", "none", false, true));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertFalse(log.stream().anyMatch(line -> line.contains("P4 Task: unshelve review:")),
				"ignoreEmpty should skip the unshelve task entirely when the shelf resolves to blank");
	}

	@Test
	void performFailsBuildWhenScmIsNotPerforce() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("UnshelveNoP4Scm");
		project.getBuildersList().add(new UnshelveBuilder("1", "none", false, false));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.FAILURE, build.getResult());
	}

	@Test
	void pipelineUnshelveFallsBackToTagActionCredentialAndWorkspaceWhenOmitted() throws Exception {
		String base = "//depot/UnshelveBuilderTest/pipeline";
		String jfile = base + "/Jenkinsfile";

		submitFile(jenkins, base + "/sync/file1", "content");
		String shelveId = shelveFile(jenkins, base + "/unshelve/file2", "shelved-content");

		String jFileContent = "pipeline {\n" +
				"  agent any\n" +
				"  stages {\n" +
				"    stage(\"Repro\") {\n" +
				"      steps {\n" +
				"        script {\n" +
				"          p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n" +
				"            populate: autoClean(delete: true, replace: true),\n" +
				"            source: depotSource('" + base + "/...')\n" +
				"          p4unshelve resolve: 'at', shelf: '" + shelveId + "'\n" +
				"        }\n" +
				"      }\n" +
				"    }\n" +
				"  }\n" +
				"}";

		submitFile(jenkins, jfile, jFileContent);

		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}-pipeline-unshelve";
		PerforceScm scm = scmForView(client, base + "/... //" + client + "/...");
		scm.getDescriptor().setLastSuccess(true);

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PipelineUnshelveFallback");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);
		WorkflowRun run = job.scheduleBuild2(0).get();
		waitForBuild(job, run.getNumber());

		jenkins.assertBuildStatusSuccess(run);

		List<String> log = run.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: " + shelveId),
				"p4unshelve should have used the sync step's credential/workspace from TagAction");
		assertTrue(log.stream().anyMatch(line -> line.contains(base + "/unshelve/file2")),
				"the shelved file should have been unshelved into the sync step's workspace");
	}

	@Test
	void deprecatedConstructorsDelegateWithSafeDefaults() {
		UnshelveBuilder threeArg = new UnshelveBuilder("10", "am", true);
		assertEquals("10", threeArg.getShelf());
		assertEquals("am", threeArg.getResolve());
		assertTrue(threeArg.isTidy());
		assertFalse(threeArg.isIgnoreEmpty());

		UnshelveBuilder twoArg = new UnshelveBuilder("11", "as");
		assertEquals("11", twoArg.getShelf());
		assertEquals("as", twoArg.getResolve());
		assertFalse(twoArg.isTidy());
		assertFalse(twoArg.isIgnoreEmpty());
	}

	@Test
	void descriptorExposesResolveOptionsAndDisplayName() {
		UnshelveBuilder.DescriptorImpl descriptor = UnshelveBuilder.descriptor();
		assertTrue(descriptor.isApplicable(FreeStyleProject.class));
		assertEquals("Perforce: Unshelve", descriptor.getDisplayName());

		ListBoxModel items = UnshelveBuilder.DescriptorImpl.doFillResolveItems();
		assertEquals(6, items.size());
	}
}
