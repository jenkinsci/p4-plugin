package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.Metadata;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.ILabelMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.server.IOptionsServer;
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
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import static com.perforce.p4java.core.IMapEntry.EntryType.INCLUDE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class ConnectionTest extends DefaultEnvironment {

	private static final Logger LOGGER = Logger.getLogger(ConnectionTest.class.getName());
	private static final String P4ROOT = "tmp-ConnectionTest-p4root";
	private static P4PasswordImpl auth;

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);
    
    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
    }

	@BeforeEach
	void beforeEach() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testCheckP4d() throws Exception {
		int ver = p4d.getVersion();
		assertTrue(ver >= 20121);
	}

	@Test
	void testCredentialsList() throws Exception {
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
	void testDoCheckCredentialWithUnknownIdReturnsError() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("CredentialsUnknown");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		PerforceScm.DescriptorImpl impl = (DescriptorImpl) project.getScm().getDescriptor();
		FormValidation form = impl.doCheckCredential(project, "thisCredentialDoesNotExist");
		assertEquals(FormValidation.Kind.ERROR, form.kind);
	}

	@Test
	void testTrackingOfCredential() throws Exception {
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
	void testFreeStyleProject_buildHead() throws Exception {
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
	void testPinHost_ManualWs() throws Exception {
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
		String clienthost;
		try (ClientHelper p4 = new ClientHelper(auth, null, workspace)) {
			IClient iclient = p4.getClient();
			clienthost = iclient.getHostName();
		}
		String hostname = InetAddress.getLocalHost().getHostName();

		assertNotNull(clienthost);
		assertEquals(hostname, clienthost);
	}

	@Test
	void testTPI83() throws Exception {
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
	void testManual_Modtime() throws Exception {
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
		try (ClientHelper p4 = new ClientHelper(auth, null, workspace)) {
			boolean mod = p4.getClient().getOptions().isModtime();
			assertTrue(mod);
		}

		// Check file exists with the correct date
		String ws = build.getWorkspace().getRemote();
		File file = new File(ws + "/file-0.dat");
		assertTrue(file.exists());

		String ver = Metadata.getP4JVersionString();
		LOGGER.info("P4Java Version: " + ver);

		long epoch = file.lastModified();
		assertEquals(1397049803000L, epoch);
	}


	@Test
	void testIsCounter() throws Exception {
		try (ConnectionHelper cHelper = new ConnectionHelper(auth, null)) {
			String cName = "change";  // always exists.
			try {
				boolean isCounter = cHelper.isCounter(cName);
				assertTrue(isCounter, "counter '" + cName + "'not found");
			} catch (Exception e) {
				fail("exception checking counter " + cName + ": " + e.getMessage());
			}

			cName = "thisDoesNotExist";
			try {
				boolean isCounter = cHelper.isCounter(cName);
				assertFalse(isCounter, "counter '" + cName + "' found");
			} catch (Exception e) {
				fail("exception checking counter " + cName + ": " + e.getMessage());
			}

			cName = "666111"; // JENKINS-70219
			Scanner scanner = null;
			try {
				boolean isCounter = cHelper.isCounter(cName);
				assertFalse(isCounter, "counter '" + cName + "' found");

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

	@Test
	void testIsLabelAndLabelToChange() throws Exception {
		try (ConnectionHelper cHelper = new ConnectionHelper(auth, null)) {
			assertFalse(cHelper.isLabel("now"));
			assertFalse(cHelper.isLabel("thisLabelDoesNotExist"));
			assertNull(cHelper.labelToChange("thisLabelDoesNotExist"));

			// Label pinned to a specific change: RevisionSpec present, leading "@" stripped.
			Label pinned = new Label();
			pinned.setName("PinnedLabel");
			pinned.setDescription("pinned test label");
			pinned.setRevisionSpec("@2");
			pinned.setViewMapping(singleDepotViewMapping());
			cHelper.setLabel(pinned);

			assertTrue(cHelper.isLabel("PinnedLabel"));
			assertEquals("2", cHelper.labelToChange("PinnedLabel"));

			// Static label (no RevisionSpec): labelToChange falls back to the label's own name.
			Label unpinned = new Label();
			unpinned.setName("StaticLabel");
			unpinned.setDescription("static test label");
			unpinned.setViewMapping(singleDepotViewMapping());
			cHelper.setLabel(unpinned);

			assertTrue(cHelper.isLabel("StaticLabel"));
			assertEquals("StaticLabel", cHelper.labelToChange("StaticLabel"));
		}
	}

	private ViewMap<ILabelMapping> singleDepotViewMapping() {
		ViewMap<ILabelMapping> viewMapping = new ViewMap<>();
		Label.LabelMapping mapping = new Label.LabelMapping();
		mapping.setLeft("//depot/...");
		mapping.setType(INCLUDE);
		viewMapping.addEntry(mapping);
		return viewMapping;
	}

	@Test
	void testCounterToChange() throws Exception {
		// setting a counter needs admin rights; the "jenkins" user only reads it back.
		P4PasswordImpl admin = createCredentials("admin", "Password", p4d.getRshPort(), "testCounterToChange-admin");
		try (ConnectionHelper adminHelper = new ConnectionHelper(admin, null)) {
			IOptionsServer server = adminHelper.getConnection();
			CounterOptions opts = new CounterOptions();
			server.setCounter("numericCounter", "7", opts);
			server.setCounter("nonNumericCounter", "abc", opts);
		}

		try (ConnectionHelper cHelper = new ConnectionHelper(auth, null)) {
			assertEquals("7", cHelper.counterToChange("numericCounter"));
			assertNull(cHelper.counterToChange("nonNumericCounter"));
			assertNull(cHelper.counterToChange("thisCounterDoesNotExist"));
		}
	}

	@Test
	void testGetLatestChangeForPollPath() throws Exception {
		try (ConnectionHelper cHelper = new ConnectionHelper(auth, null)) {
			assertNull(cHelper.getLatestChangeForPollPath(null));
			assertNull(cHelper.getLatestChangeForPollPath(new P4PollRef(-1, "//depot/PollPath/...")));
			assertNull(cHelper.getLatestChangeForPollPath(new P4PollRef(0, null)));

			String base = "//depot/PollPath";
			String firstChange = submitFile(jenkins, base + "/file1", "content");
			String secondChange = submitFile(jenkins, base + "/file2", "content");
			long first = Long.parseLong(firstChange);
			long second = Long.parseLong(secondChange);

			// pollPath already ends with "/..."
			P4PollRef fromWithWildcard = new P4PollRef(first, base + "/...");
			P4PollRef latest = cHelper.getLatestChangeForPollPath(fromWithWildcard);
			assertNotNull(latest);
			assertEquals(second, latest.getChange());

			// pollPath without a trailing "/..." (method appends it)
			P4PollRef fromBarePath = new P4PollRef(first, base);
			latest = cHelper.getLatestChangeForPollPath(fromBarePath);
			assertNotNull(latest);
			assertEquals(second, latest.getChange());

			// already at the latest change: no new changes to report
			P4PollRef atHead = new P4PollRef(second, base + "/...");
			assertNull(cHelper.getLatestChangeForPollPath(atHead));
		}
	}
}
