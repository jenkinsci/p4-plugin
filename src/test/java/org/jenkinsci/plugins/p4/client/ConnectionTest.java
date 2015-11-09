package org.jenkinsci.plugins.p4.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.FreeStyleBuild;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.p4.DummyServer;
import org.jenkinsci.plugins.p4.P4Server;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.browsers.P4WebBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
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

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.perforce.p4java.Metadata;
import com.perforce.p4java.client.IClient;

public class ConnectionTest {

	private static Logger logger = Logger.getLogger(ConnectionTest.class
			.getName());

	private final String credential = "id";
	private static String PWD = System.getProperty("user.dir") + "/";
	private static String P4BIN = "src/test/resources/r14.1/";
	private final static String P4ROOT = "tmp-p4root";
	private final static String P4PORT = "localhost:1999";
	private final static int HTTP_PORT = 1888;
	private final static String HTTP_URL = "http://localhost:" + HTTP_PORT;
	private final int LOG_LIMIT = 1000;

	private final static P4Server p4d = new P4Server(PWD + P4BIN, P4ROOT,
			P4PORT);

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
		auth = createCredentials();
	}

	@After
	public void stopServer() throws Exception {
		p4d.stop();
	}

	@AfterClass
	public static void cleanServer() throws Exception {
		p4d.clean();
	}

	private P4PasswordImpl createCredentials() throws IOException {
		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM,
				credential, "desc", P4PORT, null, "jenkins", "0", "0", "jenkins");
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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
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
		FormValidation form = impl.doTestConnection(P4PORT, "false", null,
				"jenkins", "0", "0", "jenkins");
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testPinHost_ManualWs() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false,
				false, false, stream, line, view);

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true,
				client, spec);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in with client for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, "manual.ws");
		IClient iclient = p4.getClient();
		String clienthost = iclient.getHostName();
		String hostname = InetAddress.getLocalHost().getHostName();

		assertNotNull(clienthost);
		assertEquals(hostname, clienthost);
	}

	@Test
	public void testFreeStyleProject_buildChange() throws Exception {

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Static-Change");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false,
				"test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new ParametersAction(list);

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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Static-Change");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false,
				"test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, null,
				populate, browser);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"committed"));
		list.add(new StringParameterValue(ReviewProp.LABEL.toString(), "auto15"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL
				+ "/pass"));
		Action actions = new ParametersAction(list);

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
		label.submit();

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
	public void testFreeStyleProject_buildShelf() throws Exception {

		URL url = new URL("http://localhost");
		SwarmBrowser browser = new SwarmBrowser(url);

		String client = "test.ws";
		String format = "jenkins-${node}-${project}.ws";

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Static-Shelf");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none",
				false, client, format);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();
		
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		list.add(new StringParameterValue(ReviewProp.PASS.toString(), HTTP_URL
				+ "/pass"));
		Action actions = new ParametersAction(list);

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
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false,
				false, false, stream, line, view);

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false,
				client, spec);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Template-Head");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none",
				false, client, format);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Template (view generated for each node)",
				desc.getDisplayName());

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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Stream-Head");
		StreamWorkspaceImpl workspace = new StreamWorkspaceImpl("none", false,
				stream, format);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Streams (view generated by Perforce for each node)",
				desc.getDisplayName());

		// Log in for next set of tests...
		ConnectionHelper p4 = new ConnectionHelper(auth);
		p4.login();

		FormValidation form = WorkspaceDescriptor.doCheckStreamName("//stream/main");
		assertEquals(FormValidation.Kind.OK, form.kind);

		AutoCompletionCandidates list = WorkspaceDescriptor.doAutoCompleteStreamName("//");
		assertTrue(list.getValues().contains("//stream/main"));

		form = WorkspaceDescriptor.doCheckFormat(format);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testTPI83() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("TPI83");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
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

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Template-Head");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none",
				false, client, format);
		Populate populate = new AutoCleanImpl(true, true, false, false, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"shelved"));
		list.add(new StringParameterValue(ReviewProp.REVIEW.toString(), "19"));
		Action actions = new ParametersAction(list);

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
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false,
				false, false, stream, line, view);

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false,
				client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false,
				"auto15");
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new ParametersAction(list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<Integer> buildList = scm.getChanges();
		assertEquals(12, buildList.size());
	}

	@Test
	public void testPolling_Inc() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false,
				false, false, stream, line, view);

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false,
				client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false,
				"auto15");
		List<Filter> filter = new ArrayList<Filter>();
		FilterPerChangeImpl inc = new FilterPerChangeImpl(true);
		filter.add(inc);
		PerforceScm scm = new PerforceScm(credential, workspace, filter,
				populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(),
				"submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new ParametersAction(list);

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<Integer> buildList = scm.getChanges();
		assertEquals(1, buildList.size());
		int change = buildList.get(0);
		assertEquals(4, change);
	}

	@Test
	public void testManual_Modtime() throws Exception {

		String client = "modtime.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false,
				false, false, stream, line, view);

		FreeStyleProject project = jenkins
				.createFreeStyleProject("Manual_Modtime");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false,
				client, spec);
		boolean isModtime = true;
		Populate populate = new AutoCleanImpl(true, true, isModtime, false,
				null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, client);
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

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class,
				"demo");
		job.setDefinition(new CpsFlowDefinition(
				"node {\n"
						+ "   p4sync credential: '"
						+ id
						+ "', template: 'test.ws'"
						+ "\n"
						+ "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'"
						+ "\n"
						+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]"
						+ "\n"
						+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']"
						+ "\n" + "   p4publish credential: '" + id
						+ "', publish: publisher, workspace: buildWorkspace"
						+ " \n" + "}"));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job
				.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.",
				run);
	}

	private static void startHttpServer(int port) throws Exception {
		DummyServer server = new DummyServer(port);
		new Thread(server).start();
	}
}
