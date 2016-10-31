package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.perforce.p4java.Metadata;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.scm.PollingResult;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.DummyServer;
import org.jenkinsci.plugins.p4.P4Server;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.browsers.P4WebBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterViewMaskImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionTest {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());

	private final String credential = "id";
	private static String PWD = System.getProperty("user.dir") + "/";
	private static String P4BIN = "src/test/resources/r14.1/";
	private final static String P4ROOT = "tmp-p4root";
	private final static String P4PORT = "localhost:1999";
	private final static int HTTP_PORT = 1888;
	private final static String HTTP_URL = "http://localhost:" + HTTP_PORT;
	private final int LOG_LIMIT = 1000;

	private final static P4Server p4d = new P4Server(PWD + P4BIN, P4ROOT, P4PORT);

	private P4PasswordImpl auth;

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@BeforeClass
	public static void setupServer() throws Exception {
		File ckp = new File("src/test/resources/checkpoint.gz");
		assertTrue(ckp.exists());

		File depot = new File("src/test/resources/depot.tar.gz");
		assertTrue(depot.exists());

		p4d.clean();

		File root = new File(P4ROOT);
		assertTrue(root.exists());

		p4d.restore(ckp);
		p4d.upgrade();
		p4d.extract(depot);

		// start pseudo web server
		startHttpServer(HTTP_PORT);
	}

	@Before
	public void startServer() throws Exception {
		p4d.start();
		auth = createCredentials("jenkins", "jenkins");
	}

	@After
	public void stopServer() throws Exception {
		p4d.stop();
	}

	@AfterClass
	public static void cleanServer() throws Exception {
		p4d.clean();
	}

	private P4PasswordImpl createCredentials(String user, String password) throws IOException {
		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM, credential, "desc", P4PORT, null, user,
				"0", "0", password);
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();
		return auth;
	}

	@Test
	public void testCheckP4d() throws Exception {
		int ver = p4d.getVersion();
		assertTrue(ver >= 20121);
	}

	@Test
	public void testCredentialsList() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		SCMDescriptor<?> desc = project.getScm().getDescriptor();
		assertNotNull(desc);

		PerforceScm.DescriptorImpl impl = (DescriptorImpl) desc;
		ListBoxModel list = impl.doFillCredentialItems();
		assertTrue(list.size() == 1);

		FormValidation form = impl.doCheckCredential(credential);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testFreeStyleProject_buildHead() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 40"));

		CredentialsDescriptor desc = auth.getDescriptor();
		assertNotNull(desc);
		assertEquals("Perforce Password Credential", desc.getDisplayName());
		P4PasswordImpl.DescriptorImpl impl = (P4PasswordImpl.DescriptorImpl) desc;
		FormValidation form = impl.doTestConnection(P4PORT, "false", null, "jenkins", "0", "0", "jenkins");
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testPinHost_ManualWs() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in with client for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, "manual.ws", "utf8");
		IClient iclient = p4.getClient();
		String clienthost = iclient.getHostName();
		String hostname = InetAddress.getLocalHost().getHostName();

		assertNotNull(clienthost);
		assertEquals(hostname, clienthost);
	}

	@Test
	public void testFreeStyleProject_buildChange() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Change");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Change");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, null, populate, browser);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.LABEL.toString(), "auto15"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Counter");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		String pin = "testCounter";
		Populate populate = new AutoCleanImpl(false, false, false, true, pin, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		// Log in and create counter for test
		P4PasswordImpl admin = createCredentials("admin", "Password");
		ClientHelper p4 = new ClientHelper(admin, null, "manual.ws", "utf8");
		IOptionsServer iserver = p4.getConnection();
		CounterOptions opts = new CounterOptions();
		iserver.setCounter("testCounter", "9", opts);

		UserIdCause cause = new Cause.UserIdCause();
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

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Shelf");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", false, client, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL + "/pass"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
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
	public void testTPI83() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("TPI83");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		String filename = "add_@%#$%^&().txt";

		String path = build.getWorkspace() + "/" + filename;
		File add = new File(path);
		add.createNewFile();

		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}

	@Test
	public void testTPI95() throws Exception {

		String client = "test.ws";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("Template-Head");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", false, client, format);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: unshelve review: 19"));

		// TPI-95 Second build with template ws
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}

	@Test
	public void testPolling_Pin() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, "auto15", null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Revision> buildList = scm.getIncrementalChanges();
		assertEquals(12, buildList.size());
	}

	@Test
	public void testPolling_Inc() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, "auto15", null);
		List<Filter> filter = new ArrayList<Filter>();
		FilterPerChangeImpl inc = new FilterPerChangeImpl(true);
		filter.add(inc);
		PerforceScm scm = new PerforceScm(credential, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		PollingResult found = project.poll(listener);
		assertEquals(PollingResult.BUILD_NOW, found);

		// Build now
		build = project.scheduleBuild2(0, cause).get();
		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 4"));
	}

	@Test
	public void testPolling_Mask() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		// Filter changes outside of //depot/Data path
		FilterViewMaskImpl mask = new FilterViewMaskImpl("//depot/Data");
		filter.add(mask);
		PerforceScm scm = new PerforceScm(credential, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Revision> buildList = scm.getIncrementalChanges();
		assertEquals(2, buildList.size());
		int change = buildList.get(0).getChange();
		assertEquals(18, change);
	}

	@Test
	public void testPolling_Mask_Excl() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		// Filter changes outside of //depot/Main and also under //depot/Main/TPI-83
		StringBuilder sb = new StringBuilder();
		sb.append("//depot/Main");
		sb.append("\n");
		sb.append("-//depot/Main/TPI-83");

		FilterViewMaskImpl mask = new FilterViewMaskImpl(sb.toString());
		filter.add(mask);
		PerforceScm scm = new PerforceScm(credential, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// TODO: determine the CL our mask should poll us at
		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Revision> buildList = scm.getIncrementalChanges();
		assertEquals(13, buildList.size());
		int change = buildList.get(0).getChange();
		assertEquals(16, change);
	}

	@Test
	public void testManual_Modtime() throws Exception {

		String client = "modtime.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual_Modtime");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
		boolean isModtime = true;
		Populate populate = new AutoCleanImpl(true, true, isModtime, false, null, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, client, "utf8");
		boolean mod = p4.getClient().getOptions().isModtime();
		assertEquals(true, mod);

		// Check file exists with the correct date
		String ws = build.getWorkspace().getRemote();
		File file = new File(ws + "/file-0.dat");
		assertEquals(true, file.exists());

		String ver = Metadata.getP4JVersionString();
		logger.info("P4Java Version: " + ver);

		long epoch = file.lastModified();
		assertEquals(1397049803000L, epoch);
	}

	@Test
	public void testWorkflow() throws Exception {

		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
		job.setDefinition(new CpsFlowDefinition("node {\n" + "   p4sync credential: '" + id + "', template: 'test.ws'"
				+ "\n" + "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'" + "\n"
				+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]"
				+ "\n"
				+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']"
				+ "\n" + "   p4publish credential: '" + id + "', publish: publisher, workspace: buildWorkspace" + " \n"
				+ "}"));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.", run);
	}

	@Test
	public void shouldNotTriggerJobIfNoChange() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Change");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		PerforceScm scm = new PerforceScm(auth.getId(), workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Run once
		jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause()));

		// Test trigger
		trigger.poke(project, auth.getP4port());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Shouldn't have triggered a build if no change", 1, project.getLastBuild().getNumber());
	}

	@Test
	public void shouldTriggerJobIfChanges() throws Exception {
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		PerforceScm scm = new PerforceScm(auth.getId(), workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Checkout at commit 9
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		// Run once
		Run lastRun = jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause(), actions));
		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(project, auth.getP4port());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on change", 2, project.getLastBuild().getNumber());
	}

	@Test
	public void testShouldTriggerPipelineJobIfChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
		job.setDefinition(new CpsFlowDefinition("" + "node {\n" + "   p4sync credential: '" + auth.getId()
				+ "', depotPath: '//depot', format: 'test.ws'\n" + "}"));

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		WorkflowRun lastRun = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Hack to make polling believe there are remote changes: sync the
		// client 'test.ws' at an anterior revision to test the trigger
		ClientHelper p4 = new ClientHelper(auth, null, "test.ws", "utf8");
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		p4.syncFiles(new P4Revision("9"), populate);

		// Test trigger
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on changes", 2, job.getLastBuild().getNumber());
	}

	@Test
	public void testShouldNotTriggerPipelineIfNoChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
		job.setDefinition(new CpsFlowDefinition(
				"" + "node {\n" + "   p4sync credential: '" + auth.getId() + "', template: 'test.ws'" + "\n" + "}"));

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		WorkflowRun lastRun = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Shouldn't have triggered a build as no changes", 1, job.getLastBuild().getNumber());
	}

	@Test
	public void testP4GroovyConnectAndSync() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p4groovy");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n"
				+ "   ws = [$class: 'StreamWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//stream/main']\n"
				+ "   p4 = p4(credential: '" + auth.getId() + "', workspace: ws)\n"
				+ "   p4.run('sync', '//...')\n"
				+ "}"));
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
				+ "   p4 = p4(credential: '" + auth.getId() + "', workspace: ws)\n"
				+ "   clientName = p4.getClientName();\n"
				+ "   client = p4.fetch('client', clientName)\n"
				+ "   echo \"Client: ${client}\""
				+ "   client.put('Description', 'foo')"
				+ "   p4.save('client', client)"
				+ "}"));
		job.save();

		WorkflowRun run = job.scheduleBuild2(0).get();
		jenkins.assertLogContains("p4 client -o jenkins-master-p4groovy.spec", run);
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
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact("artifact.1", "content"));
		project.getBuildersList().add(new CreateArtifact("artifact.2", "content"));

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, true, "3");
		PublishNotifier publish = new PublishNotifier(credential, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 18"));
		assertTrue(log.contains("p4 reopen -c41 -t+S3 //manual.ws/..."));
		assertTrue(log.contains("... submitting files"));
		assertTrue(log.contains("p4 describe -s 41"));
	}

	private static final class CreateArtifact extends Builder {
		private final String filename;
		private final String content;

		public CreateArtifact(String filename, String content) {
			this.filename = filename;
			this.content = content;
		}

		@Override
		public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
			build.getWorkspace().child(filename).write(content, "UTF-8");
			return true;
		}
	}

	private static void startHttpServer(int port) throws Exception {
		DummyServer server = new DummyServer(port);
		new Thread(server).start();
	}
}
