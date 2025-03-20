package org.jenkinsci.plugins.p4.client;

import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.SpecWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WorkspaceTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-WorkspaceTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testFreeStyleProject_ManualWs() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view, null, null, null, true);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Manual (custom view)", desc.getDisplayName());

		Descriptor<WorkspaceSpec> descSpec = spec.getDescriptor();
		assertNotNull(descSpec);
		assertEquals("Perforce Client Spec", descSpec.getDisplayName());

		// Log in for next set of tests...
		try(ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null)) {
			WorkspaceSpec.DescriptorImpl implSpec = (WorkspaceSpec.DescriptorImpl) descSpec;
			AutoCompletionCandidates list = implSpec.doAutoCompleteStreamName("//");
			assertTrue(list.getValues().contains("//stream/main"));

			ListBoxModel lineItems = implSpec.doFillLineItems();
			assertFalse(lineItems.isEmpty());

			ListBoxModel typeItems = implSpec.doFillTypeItems();
			assertFalse(typeItems.isEmpty());

			ManualWorkspaceImpl.DescriptorImpl impl = (ManualWorkspaceImpl.DescriptorImpl) desc;
			FormValidation form = impl.doCheckName("test.ws");
			assertEquals(FormValidation.Kind.OK, form.kind);

			list = impl.doAutoCompleteName("m");
			assertTrue(list.getValues().contains(client));

			JSONObject json = workspace.getSpecJSON("test.ws");
			assertEquals("//depot/... //test.ws/...\n", json.getString("view"));
		}

		JSONObject json = workspace.getSpecJSON("test.ws");
		assertEquals("please define view...", json.getString("view"));
	}

	@Test
	public void testFreeStyleProject_TemplateWs() throws Exception {

		String client = "test.ws";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("Template-Head");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", false, client, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Template (view generated for each node)", desc.getDisplayName());

		// Log in for next set of tests...
		ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null);
		p4.login();

		TemplateWorkspaceImpl.DescriptorImpl impl = (TemplateWorkspaceImpl.DescriptorImpl) desc;
		FormValidation form = impl.doCheckTemplateName("test.ws");
		assertEquals(FormValidation.Kind.OK, form.kind);

		AutoCompletionCandidates list = WorkspaceDescriptor.doAutoCompleteTemplateName("t");
		assertTrue(list.getValues().contains("test.ws"));

		form = WorkspaceDescriptor.doCheckFormat(format);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testFreeStyleProject_StreamWs() throws Exception {

		String stream = "//stream/main";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("Stream-Head");
		StreamWorkspaceImpl workspace = new StreamWorkspaceImpl("none", false, stream, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Streams (view generated by Perforce for each node)", desc.getDisplayName());

		// Log in for next set of tests...
		ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null);
		p4.login();

		FormValidation form = WorkspaceDescriptor.doCheckStreamName("//stream/main");
		assertEquals(FormValidation.Kind.OK, form.kind);

		AutoCompletionCandidates list = WorkspaceDescriptor.doAutoCompleteStreamName("//");
		assertTrue(list.getValues().contains("//stream/main"));

		form = WorkspaceDescriptor.doCheckFormat(format);
		assertEquals(FormValidation.Kind.OK, form.kind);

		// delete worksapce
		project.doDoWipeOutWorkspace();
	}

	@Test
	public void testTPI95() throws Exception {

		String client = "test.ws";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("TPI95");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", false, client, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.SWARM_REVIEW.toString(), "19"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: 19"));

		// TPI-95 Second build with template ws
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}

	@Test
	public void testFreeStyleProject_SpecWs() throws Exception {

		String client = "jenkins-${JOB_NAME}";
		String specPath = "//depot/spec/test1";

		String specFile = """
				\
				Client: jenkins-${JOB_NAME}
				Owner: pallen
				Root: /tmp
				Options:	noallwrite noclobber nocompress unlocked nomodtime rmdir
				SubmitOptions: submitunchanged
				LineEnd:	local
				View:
				\t//depot/Data/... //jenkins-${JOB_NAME}/...
				""";

		submitFile(jenkins, specPath, specFile);

		FreeStyleProject project = jenkins.createFreeStyleProject("Spec-Head");
		SpecWorkspaceImpl workspace = new SpecWorkspaceImpl("none", false, client, specPath);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Spec File (load workspace spec from file in Perforce)", desc.getDisplayName());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 18"));
		assertTrue(log.contains("... totalFileCount 6"));
	}

	@Test
	public void testFreeStyleProject_SpecWsChangeView() throws Exception {

		String client = "jenkins-${JOB_NAME}";
		String specPath = "//depot/spec/test2";

		String specFile = """
				\
				Client: ${P4_CLIENT}
				Owner: pallen
				Root: /tmp
				Options:	noallwrite noclobber nocompress unlocked nomodtime rmdir
				SubmitOptions: submitunchanged
				LineEnd:	local
				View:
				\t//depot/Data/... //${P4_CLIENT}/...
				ChangeView:
				\t//depot/Data/...@17
				""";

		submitFile(jenkins, specPath, specFile);

		FreeStyleProject project = jenkins.createFreeStyleProject("Spec-ChangeView");
		SpecWorkspaceImpl workspace = new SpecWorkspaceImpl("none", false, client, specPath);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Spec File (load workspace spec from file in Perforce)", desc.getDisplayName());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 17"));
	}

	@Test
	public void testFreeStyleProject_SpecWsBadSpec() throws Exception {

		String client = "jenkins-${JOB_NAME}";
		String specPath = "//depot/spec/test3";

		String specFile = """
				\
				Client: bad_client
				Owner: pallen
				Root: /tmp
				Options:	noallwrite noclobber nocompress unlocked nomodtime rmdir
				SubmitOptions: submitunchanged
				LineEnd:	local
				View:
				\t//depot/Data/... //bad_client/...
				""";

		submitFile(jenkins, specPath, specFile);

		FreeStyleProject project = jenkins.createFreeStyleProject("Spec-BadClient");
		SpecWorkspaceImpl workspace = new SpecWorkspaceImpl("none", false, client, specPath);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.FAILURE, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("ERROR: P4: Unable to initialise CheckoutTask: hudson.AbortException: Spec file does not match client."));
	}

	@Test
	public void testSyncID() {
		Map<String, String> map = new HashMap<>();
		map.put("NODE_NAME", "foo");
		map.put("OTHER", "bar");

		String stream = "//stream/main";
		String format = "jenkins-${NODE_NAME}-${OTHER}.ws.clone2";

		StreamWorkspaceImpl workspace = new StreamWorkspaceImpl("none", false, stream, format);
		workspace.setExpand(map);

		assertEquals("jenkins-NODE_NAME-bar.ws", workspace.getSyncID());
	}

	@Test
	public void testFolderProject_StreamWs() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}.ws";
		String view = "//depot/Data/... //" + format + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, format, spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		FreeStyleProject project = jenkins.createFreeStyleProject("Folder test");
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}
}
