package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.client.AuthorisationConfig;
import org.jenkinsci.plugins.p4.client.AuthorisationType;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PerforceCredentialsTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-CredentialsTest-p4root";

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Test
	public void testAddStandardCredentials() throws IOException {
		P4BaseCredentials credential = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "id", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertEquals("desc:passwd", credential.getDescription());
		assertEquals("id", credential.getId());

		List<P4BaseCredentials> list = lookupCredentials();
		assertEquals("localhost:1666", list.get(0).getFullP4port());
		assertEquals("user", list.get(0).getUsername());
		assertNull(list.get(0).getSsl());

		String name = CredentialsNameProvider.name(credential);
		assertEquals("desc:passwd", name);
	}

	@Test
	public void testAddPasswordCredentials() throws IOException {
		P4PasswordImpl credential = new P4PasswordImpl(CredentialsScope.SYSTEM,
				"id", "description", "localhost:1666", null, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertEquals("pass", credential.getPassword().getPlainText());
		assertNull(credential.getSsl());

		AuthorisationConfig auth = new AuthorisationConfig(credential);
		assertEquals(AuthorisationType.PASSWORD, auth.getType());
		assertEquals("user", auth.getUsername());
		assertEquals("pass", auth.getPassword());
		assertEquals("user@no-client", auth.toString());

		// point less
		auth.setClient("client");
		assertEquals("client", auth.getClient());
		assertEquals("user@client", auth.toString());
	}

	@Test
	public void testAddSslCredentials() throws IOException {
		TrustImpl ssl = new TrustImpl("12345ABCD");
		P4PasswordImpl credential = new P4PasswordImpl(CredentialsScope.SYSTEM,
				"id", "description", "localhost:1666", ssl, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertNotNull(credential.getSsl());
		assertEquals("12345ABCD", credential.getTrust());
	}

	@Test
	public void testAddTicketCredentials() throws IOException {
		TicketModeImpl ticket = new TicketModeImpl("ticketValueSet", "12345",
				null);

		P4TicketImpl credential = new P4TicketImpl(CredentialsScope.SYSTEM,
				"id", "desc:ticket", "localhost:1666", null, "user", "0", "0", null, ticket);

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertEquals("12345", credential.getTicketValue());

		AuthorisationConfig auth = new AuthorisationConfig(credential);
		assertEquals(AuthorisationType.TICKET, auth.getType());
		assertEquals("12345", auth.getTicketValue());

		String name = CredentialsNameProvider.name(credential);
		assertEquals("desc:ticket", name);
	}

	@Test
	public void testAddTicketPathCredentials() throws IOException {
		TicketModeImpl ticket = new TicketModeImpl("ticketPathSet", null,
				"~/.p4ticket");

		P4TicketImpl credential = new P4TicketImpl(CredentialsScope.SYSTEM,
				"id", "description", "localhost:1666", null, "user", "0", "0", null, ticket);

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertEquals("~/.p4ticket", credential.getTicketPath());

		AuthorisationConfig auth = new AuthorisationConfig(credential);
		assertEquals(AuthorisationType.TICKETPATH, auth.getType());
		assertEquals("~/.p4ticket", auth.getTicketPath());
	}

	private List<P4BaseCredentials> lookupCredentials() {
		Class<P4BaseCredentials> type = P4BaseCredentials.class;
		Jenkins scope = Jenkins.get();
		Authentication acl = ACL.SYSTEM2;
		DomainRequirement domain = new DomainRequirement();

		return CredentialsProvider.lookupCredentialsInItemGroup(type, scope, acl, List.of(domain));
	}

	@Test
	public void testAvailableCredentialsAtRoot() throws IOException {
		P4BaseCredentials systemCredentials = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "idSystem", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		P4BaseCredentials globalCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInGlobal", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		List<P4BaseCredentials> list = lookupCredentials();
		assertEquals(2, list.size());
	}

	@Test
	public void testAvailableCredentialsInJob() throws IOException {
		Job job = jenkins.createFreeStyleProject("testAvailableCredentialsInJob");

		P4BaseCredentials systemCredentials = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "idSystem", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		P4BaseCredentials globalCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInGlobal", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		List<P4BaseCredentials> list = CredentialsProvider.lookupCredentialsInItem(P4BaseCredentials.class,
				job, ACL.SYSTEM2, Collections.emptyList());
		assertEquals(1, list.size());
		assertEquals(globalCredentials.getId(), list.get(0).getId());
	}

	@Test
	public void testAvailableCredentialsInFolder() throws IOException {

		Folder folder = createFolder();
		CredentialsStore folderStore = getFolderStore(folder);

		P4BaseCredentials systemCredentials = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "idSystem", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		P4BaseCredentials globalCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInGlobal", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idSystem", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());
		folderStore.addCredentials(Domain.global(), inFolderCredentials);
		folder.save();
		assertFalse(folderStore.getCredentials(Domain.global()).isEmpty());

		List<P4BaseCredentials> list = CredentialsProvider.lookupCredentialsInItemGroup(P4BaseCredentials.class,
				folder.getItemGroup(), ACL.SYSTEM2, Collections.emptyList());
		assertEquals(2, list.size());
		assertEquals(inFolderCredentials.getId(), list.get(0).getId());
		assertEquals(globalCredentials.getId(), list.get(1).getId());
	}

	@Test
	public void testInJobCredentialsList() throws Exception {

		String port = p4d.getRshPort();

		P4BaseCredentials systemCredentials = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "idSystem", "desc:passwd", port,
				null, "jenkins", "0", "0", null, "jenkins");
		P4BaseCredentials globalCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInGlobal", "desc:passwd", port,
				null, "jenkins", "0", "0", null, "jenkins");

		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();

		FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "testInJobCredentialsList");

		ListBoxModel list = P4CredentialsImpl.doFillCredentialItems(project, null);
		assertEquals(2, list.size());
		list = P4CredentialsImpl.doFillCredentialItems(project, globalCredentials.getId());
		assertEquals(2, list.size());

		FormValidation form = P4CredentialsImpl.doCheckCredential(project, null);
		assertEquals(FormValidation.Kind.OK, form.kind);
		form = P4CredentialsImpl.doCheckCredential(project, globalCredentials.getId());
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	@Test
	public void testInFolderCredentialsList() throws Exception {

		String port = p4d.getRshPort();

		// Create a folder with credentials in store
		Folder folder = createFolder();
		CredentialsStore folderStore = getFolderStore(folder);
		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInFolder", "desc:passwd", port,
				null, "jenkins", "0", "0", null, "jenkins");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);
		P4BaseCredentials systemCredentials = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "idSystem", "desc:passwd", port,
				null, "jenkins", "0", "0", null, "jenkins");
		P4BaseCredentials globalCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInGlobal", "desc:passwd", port,
				null, "jenkins", "0", "0", null, "jenkins");

		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();

		FreeStyleProject project = folder.createProject(FreeStyleProject.class, "testInFolderCredentialsList");
		ListBoxModel list = P4CredentialsImpl.doFillCredentialItems(project, null);
		assertEquals(3, list.size());

		list = P4CredentialsImpl.doFillCredentialItems(project, inFolderCredentials.getId());
		assertEquals(3, list.size());

		FormValidation form = P4CredentialsImpl.doCheckCredential(project, null);
		assertEquals(FormValidation.Kind.OK, form.kind);

		form = P4CredentialsImpl.doCheckCredential(project, inFolderCredentials.getId());
		assertEquals(FormValidation.Kind.OK, form.kind);
	}

	private Folder createFolder() throws IOException {
		return jenkins.jenkins.createProject(Folder.class, "folder" + jenkins.jenkins.getItems().size());
	}

	private CredentialsStore getFolderStore(Folder f) {
		Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
		CredentialsStore folderStore = null;
		for (CredentialsStore s : stores) {
			if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
				folderStore = s;
				break;
			}
		}
		return folderStore;
	}

	@Test
	public void testInvalidCredentials() throws Exception {

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "invalidCredentials");
		job.setDefinition(new CpsFlowDefinition("""
				\
				node {
				   p4sync credential: 'Invalid', template: 'test.ws'
				   println "P4_CHANGELIST: ${env.P4_CHANGELIST}"
				}""", false));
		WorkflowRun run = job.scheduleBuild2(0).get();
		assertEquals(Result.FAILURE, run.getResult());
		jenkins.assertLogContains("Unable to checkout: org.jenkinsci.plugins.p4.credentials.P4InvalidCredentialException: Invalid credentials", run);
	}

	@Test
	public void testInvalidPort() throws Exception {
		createCredentials("user", "password", "localhos:1666", "idBad");

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "invalidPort");
		job.setDefinition(new CpsFlowDefinition("""
				\
				node {
				   p4sync credential: 'idBad', template: 'test.ws'
				   println "P4_CHANGELIST: ${env.P4_CHANGELIST}"
				}""", false));
		WorkflowRun run = job.scheduleBuild2(0).get();
		assertEquals(Result.FAILURE, run.getResult());
		jenkins.assertLogContains("Unable to ", run);
	}

	@Test
	public void testConnectionError() {
		try {
			P4PasswordImpl cred = createCredentials("user", "password", p4d.getRshPort(), "InvalidUserPass");
			//helper is not used but is required to call the constructor to trigger the flow.
			ConnectionHelper helper = new ConnectionHelper(cred);
		} catch (Exception e) {
			assertEquals("P4: Invalid credentials. Giving up...", e.getMessage());
		}
	}
}
