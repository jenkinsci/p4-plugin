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
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
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
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by pallen on 01/11/2016.
 */
public class WorkspaceTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(PollingTest.class.getName());
	private static final String P4ROOT = "tmp-WorkspaceTest-p4root";
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
	public void testFreeStyleProject_ManualWs() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
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
		ConnectionHelper p4 = new ConnectionHelper(auth);
		p4.login();

		WorkspaceSpec.DescriptorImpl implSpec = (WorkspaceSpec.DescriptorImpl) descSpec;
		AutoCompletionCandidates list = implSpec.doAutoCompleteStreamName("//");
		assertTrue(list.getValues().contains("//stream/main"));

		ListBoxModel lineItems = implSpec.doFillLineItems();
		assertFalse(lineItems.isEmpty());

		ManualWorkspaceImpl.DescriptorImpl impl = (ManualWorkspaceImpl.DescriptorImpl) desc;
		FormValidation form = impl.doCheckName("test.ws");
		assertEquals(FormValidation.Kind.OK, form.kind);

		list = impl.doAutoCompleteName("m");
		assertTrue(list.getValues().contains(client));

		JSONObject json = workspace.getSpecJSON("test.ws");
		assertEquals("//depot/... //test.ws/...\n", json.getString("view"));

		p4.disconnect();
		json = workspace.getSpecJSON("test.ws");
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
		ConnectionHelper p4 = new ConnectionHelper(auth);
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
		ConnectionHelper p4 = new ConnectionHelper(auth);
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

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

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

}
