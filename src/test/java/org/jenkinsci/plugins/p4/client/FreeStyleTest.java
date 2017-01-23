package org.jenkinsci.plugins.p4.client;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
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
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.browsers.P4WebBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FreeStyleTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(FreeStyleTest.class.getName());
	private static final String P4ROOT = "tmp-FreeStyleTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, VERSION);

	@BeforeClass
	public static void startWebServer() throws Exception {
		// start pseudo web server
		startHttpServer(HTTP_PORT);
	}

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
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
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 9"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asText();
		assertTrue(text.contains("9 by jenkins@jenkins.data.ws"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asText();
		assertTrue(text.contains("//depot/Main/file-11.txt#4"));

		// Check workspace descriptors
		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Static (static view, master only)", desc.getDisplayName());
		ListBoxModel charsets = WorkspaceDescriptor.doFillCharsetItems();
		assertTrue(charsets.size() > 1);

		// Log in for next set of tests...
		ConnectionHelper p4 = new ConnectionHelper(auth);
		p4.login();

		StaticWorkspaceImpl.DescriptorImpl impl = (StaticWorkspaceImpl.DescriptorImpl) desc;
		FormValidation form = impl.doCheckName("test.ws");
		assertEquals(FormValidation.Kind.OK, form.kind);

		AutoCompletionCandidates clients = impl.doAutoCompleteName("j");
		assertTrue(clients.getValues().contains("jenkins.data.ws"));
	}



	@Test
	public void testFreeStyleProject_buildLabel() throws Exception {

		URL url = new URL("http://localhost");
		P4WebBrowser browser = new P4WebBrowser(url);

		FreeStyleProject project = jenkins.createFreeStyleProject("buildLabel");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, null, populate, browser, null, null);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.LABEL.toString(), "auto15"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 15"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asText();
		assertTrue(text.contains("15 by jenkins@jenkins.data.ws"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asText();
		assertTrue(text.contains("//depot/Main/file-14.txt #7"));

		page = jenkins.createWebClient().getPage(build, "tagBuild");
		HtmlForm label = page.getFormByName("label");
		HtmlInput input = label.getInputByName("labelSubmit");
		input.click();

		page = jenkins.createWebClient().getPage(build, "tagBuild");
		text = page.asText();
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
		Populate populate = new AutoCleanImpl(false, false, false, true, pin, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Log in and create counter for test
		P4PasswordImpl admin = createCredentials("admin", "Password", p4d);
		ClientHelper p4 = new ClientHelper(admin, null, "manual.ws", "utf8");
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

		URL url = new URL("http://localhost");
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
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: 19"));

		// Check web pages for changes
		HtmlPage page = jenkins.createWebClient().getPage(build);
		String text = page.asText();
		assertTrue(text.contains("19 by admin@admin.ws"));

		page = jenkins.createWebClient().getPage(build, "changes");
		text = page.asText();
		assertTrue(text.contains("Shelved Files:"));

		// Check browser
		Descriptor<RepositoryBrowser<?>> desc = browser.getDescriptor();
		assertNotNull(desc);

		SwarmBrowser.DescriptorImpl impl = (SwarmBrowser.DescriptorImpl) desc;
		FormValidation form = impl.doCheck(url.toString());
		assertEquals(FormValidation.Kind.OK, form.kind);
	}


}
