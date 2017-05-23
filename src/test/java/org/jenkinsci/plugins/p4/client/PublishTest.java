package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class PublishTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(FreeStyleTest.class.getName());
	private static final String P4ROOT = "tmp-PublishTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d);
	}

	@Test
	public void testPublishWithPurge() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-purge");

		// Create workspace
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// Populate with P4 scm
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact("artifact.1", "content"));
		project.getBuildersList().add(new CreateArtifact("artifact.2", "content"));

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, true, "3");
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Stat file and check type
		ClientHelper p4 = new ClientHelper(project, CREDENTIAL, null, client, "none");
		List<IFileSpec> fileSpec = FileSpecBuilder.makeFileSpecList("//depot/Data/artifact.1");
		GetExtendedFilesOptions opts = new GetExtendedFilesOptions();
		List<IExtendedFileSpec> eSpec = p4.connection.getExtendedFiles(fileSpec, opts);
		assertEquals(eSpec.get(0).getHeadType(), "text+S3");
	}
}
