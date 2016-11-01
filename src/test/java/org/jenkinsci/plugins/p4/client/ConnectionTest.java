package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.perforce.p4java.Metadata;
import com.perforce.p4java.client.IClient;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.PerforceScm.DescriptorImpl;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-ConnectionTest-p4root";
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
	public void testCheckP4d() throws Exception {
		int ver = p4d.getVersion();
		assertTrue(ver >= 20121);
	}

	@Test
	public void testCredentialsList() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		SCMDescriptor<?> desc = project.getScm().getDescriptor();
		assertNotNull(desc);

		PerforceScm.DescriptorImpl impl = (DescriptorImpl) desc;
		ListBoxModel list = impl.doFillCredentialItems();
		assertTrue(list.size() == 1);

		FormValidation form = impl.doCheckCredential(CREDENTIAL);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testFreeStyleProject_buildHead() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("Static-Head");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
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
		FormValidation form = impl.doTestConnection(p4d.getRshPort(), "false", null, "jenkins", "0", "0", "jenkins");
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
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
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
	public void testTPI83() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("TPI83");
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
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
	public void testManual_Modtime() throws Exception {

		String client = "modtime.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/Data/... //" + client + "/...";

		// The test was designed for pre 15.1 modtime checks.  Since RSH requires 15.1
		// the test is not required, however later assets have some use.  The pre20151
		// bool 'fakes' the test and allows the other checks to pass.
		boolean pre20151 = false;
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, !pre20151, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual_Modtime");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
		boolean isModtime = true;
		Populate populate = new AutoCleanImpl(true, true, isModtime, false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
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
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + id + "', template: 'test.ws'\n"
				+ "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'\n"
				+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]\n"
				+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']\n"
				+ "   p4publish credential: '" + id + "', publish: publisher, workspace: buildWorkspace" + " \n"
				+ "}"));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.", run);
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
}
