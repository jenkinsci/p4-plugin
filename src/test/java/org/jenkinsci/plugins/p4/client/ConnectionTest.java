package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.Metadata;
import com.perforce.p4java.client.IClient;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Fingerprint;
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
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConnectionTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-ConnectionTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testCheckP4d() throws Exception {
		int ver = p4d.getVersion();
		assertTrue(ver >= 20121);
	}

	@Test
	public void testCredentialsList() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("CredentialsList");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		SCMDescriptor<?> desc = project.getScm().getDescriptor();
		assertNotNull(desc);

		// Dropdown should show 2 credentials: none and "id"
		PerforceScm.DescriptorImpl impl = (DescriptorImpl) desc;
		ListBoxModel list = impl.doFillCredentialItems(project, null);
		assertEquals(2, list.size());

		list = impl.doFillCredentialItems(project, CREDENTIAL);
		assertEquals(2, list.size());

		FormValidation form = impl.doCheckCredential(project, null);
		assertEquals(FormValidation.Kind.OK, form.kind);

		form = impl.doCheckCredential(project, CREDENTIAL);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testTrackingOfCredential() throws Exception {

		P4BaseCredentials credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "testTrackingOfCredential", "desc:passwd", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		SystemCredentialsProvider.getInstance().getCredentials().add(credential);

		Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credential);
		assertThat("No fingerprint created until first use", fingerprint, nullValue());

		FreeStyleProject job = jenkins.createFreeStyleProject("testTrackingOfCredential");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential.getId(), workspace, populate);
		job.setScm(scm);
		job.save();

		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		fingerprint = CredentialsProvider.getFingerprintOf(credential);
		assertThat(fingerprint, notNullValue());
		assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));
		Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(job);
		assertThat(rangeSet, notNullValue());
		assertThat(rangeSet.includes(job.getLastBuild().getNumber()), is(true));
	}

	@Test
	public void testFreeStyleProject_buildHead() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("BuildHead");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
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
		FormValidation form = impl.doTestConnection(p4d.getRshPort(), "false", null, null, "jenkins", "jenkins", false);
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testPinHost_ManualWs() throws Exception {

		String client = "manual.ws";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual-Head");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in with client for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, workspace);
		IClient iclient = p4.getClient();
		String clienthost = iclient.getHostName();
		String hostname = InetAddress.getLocalHost().getHostName();

		assertNotNull(clienthost);
		assertEquals(hostname, clienthost);
	}

	@Test
	public void testTPI83() throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject("TPI83");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
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
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, !pre20151, false, stream, line, view, null, null, null, true);

		FreeStyleProject project = jenkins.createFreeStyleProject("Manual_Modtime");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		boolean isModtime = true;
		Populate populate = new AutoCleanImpl(true, true, false, isModtime, false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Log in for next set of tests...
		ClientHelper p4 = new ClientHelper(auth, null, workspace);
		boolean mod = p4.getClient().getOptions().isModtime();
		assertTrue(mod);

		// Check file exists with the correct date
		String ws = build.getWorkspace().getRemote();
		File file = new File(ws + "/file-0.dat");
		assertTrue(file.exists());

		String ver = Metadata.getP4JVersionString();
		logger.info("P4Java Version: " + ver);

		long epoch = file.lastModified();
		assertEquals(1397049803000L, epoch);
	}


	@Test
	public void testIsCounter() throws Exception {

		ConnectionHelper cHelper = new ConnectionHelper(auth, null);

		String cName = "change";  // always exists.
		try {
			boolean isCounter = cHelper.isCounter(cName);
			assertTrue("counter '" + cName + "'not found", isCounter);
		} catch (Exception e) {
			fail("exception checking counter " + cName + ": " + e.getMessage());
		}

		cName = "thisDoesNotExist";
		try {
			boolean isCounter = cHelper.isCounter(cName);
			assertFalse("counter '" + cName + "' found", isCounter);
		} catch (Exception e) {
			fail("exception checking counter " + cName + ": " + e.getMessage());
		}

		cName = "666111"; // JENKINS-70219
		Scanner scanner = null;
		try {
			boolean isCounter = cHelper.isCounter(cName);
			assertFalse("counter '" + cName + "' found", isCounter);

			// check log for "user-counter NNN" command.
			String lookFor = "user-counter " + cName;
			scanner = new Scanner(new File(p4d.getLogPath()));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (line.contains(lookFor)) {
					fail("Found numeric counter '" + cName + "' in log: " + line);
				}
			}
		} catch (Exception e) {
			fail("exception checking counter " + cName + ": " + e.getMessage());
		} finally {
			if (scanner != null) {
				scanner.close();
			}
		}
	}
}
