package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.browsers.P4WebBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
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

public class FreeStyleTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(FreeStyleTest.class.getName());
	private static final String P4ROOT = "tmp-FreeStyleTest-p4root";
	private static final String SUPER = "super";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
		createCredentials("admin", "Password", p4d.getRshPort(), SUPER);
	}

	@Test
	public void testFreeStyleProject_buildChange() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("BuildChange");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 9"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asNormalizedText();
		assertTrue(text.contains("9 by jenkins (jenkins.data.ws)"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asNormalizedText();
		assertTrue(text.contains("//depot/Main/file-11.txt #4"));

		// Check workspace descriptors
		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Static (static view, master only)", desc.getDisplayName());
		ListBoxModel charsets = WorkspaceDescriptor.doFillCharsetItems();
		assertTrue(charsets.size() > 1);

		// Log in for next set of tests...
		ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null);
		p4.login();

		StaticWorkspaceImpl.DescriptorImpl impl = (StaticWorkspaceImpl.DescriptorImpl) desc;
		FormValidation form = impl.doCheckName("test.ws");
		assertEquals(FormValidation.Kind.OK, form.kind);

		AutoCompletionCandidates clients = impl.doAutoCompleteName("j");
		assertTrue(clients.getValues().contains("jenkins.data.ws"));
	}



	@Test
	public void testFreeStyleProject_buildLabel() throws Exception {

		String url = "http://localhost";
		P4WebBrowser browser = new P4WebBrowser(url);

		FreeStyleProject project = jenkins.createFreeStyleProject("buildLabel");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, null, populate, browser);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.P4_LABEL.toString(), "auto15"));
		list.add(new StringParameterValue(ReviewProp.SWARM_PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 15"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asNormalizedText();
		assertTrue(text.contains("15 by jenkins (jenkins.data.ws)"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asNormalizedText();
		assertTrue(text.contains("//depot/Main/file-14.txt #7"));

		page = jenkins.createWebClient().getPage(build, "tagBuild");
		HtmlForm label = page.getFormByName("label");
		HtmlInput input = label.getInputByName("labelSubmit");
		input.click();

		page = jenkins.createWebClient().getPage(build, "tagBuild");
		text = page.asNormalizedText();
		assertTrue(text.contains("Build-1\tJenkinsBuild: #1\tjenkins\t@15"));

		// Check browser
		Descriptor<RepositoryBrowser<?>> desc = browser.getDescriptor();
		assertNotNull(desc);

		P4WebBrowser.DescriptorImpl impl = (P4WebBrowser.DescriptorImpl) desc;
		FormValidation form = impl.doCheck(url.toString());
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testFreeStyleProject_buildCounter() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("BuildCounter");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		String pin = "testCounter";
		Populate populate = new AutoCleanImpl(false, false, false, false, true, pin, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Log in and create counter for test
		ClientHelper p4 = new ClientHelper(project, SUPER, null, workspace);
		IOptionsServer iserver = p4.getConnection();
		CounterOptions opts = new CounterOptions();
		iserver.setCounter("testCounter", "9", opts);

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 9"));
	}

	@Test
	public void testFreeStyleProject_buildShelf() throws Exception {

		String url = "http://localhost";
		SwarmBrowser browser = new SwarmBrowser(url);

		String client = "test.ws";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("BuildShelf");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", false, client, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.SWARM_REVIEW.toString(), "19"));
		list.add(new StringParameterValue(ReviewProp.SWARM_PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: 19"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asNormalizedText();
		assertTrue(text.contains("19 by admin (admin.ws)"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asNormalizedText();
		assertTrue(text.contains("Shelved Files:"));

		// Check browser
		Descriptor<RepositoryBrowser<?>> desc = browser.getDescriptor();
		assertNotNull(desc);

		SwarmBrowser.DescriptorImpl impl = (SwarmBrowser.DescriptorImpl) desc;
		FormValidation form = impl.doCheck(url.toString());
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testFreeStyleProject_forceSyncOnDemand() throws Exception {


		String client = "PollingInc.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("ForceSyncOnDemand");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new SyncOnlyImpl(false, true, false, false, true, "", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue("IRRELEVALT_PARAMETER", "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertFalse(log.contains("P4: Wiping workspace..."));

		List<ParameterValue> list2 = new ArrayList<>();
		list2.add(new StringParameterValue("P4_CLEANWORKSPACE", "true"));
		Action actions2 = new SafeParametersAction(new ArrayList<ParameterValue>(), list2);

		Cause.UserIdCause cause2 = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause2, actions2).get();
		List<String> log2 = build.getLog(LOG_LIMIT);
		assertEquals(Result.SUCCESS, build.getResult());
		assertTrue(log2.contains("P4: Wiping workspace..."));
	}
}
