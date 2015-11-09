package org.jenkinsci.plugins.p4.credentials;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.security.ACL;

import java.io.IOException;
import java.util.List;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.client.AuthorisationConfig;
import org.jenkinsci.plugins.p4.client.AuthorisationType;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

public class PerforceCredentialsTest {

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Test
	public void testAddStandardCredentials() throws IOException {
		P4BaseCredentials credential = new P4PasswordImpl(
				CredentialsScope.SYSTEM, "id", "desc:passwd", "localhost:1666",
				null, "user", "0", "0", "pass");

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
		assertEquals("localhost:1666", list.get(0).getP4port());
		assertEquals("user", list.get(0).getUsername());
		assertFalse(list.get(0).isSsl());

		String name = CredentialsNameProvider.name(credential);
		assertEquals("id (desc:passwd)", name);
	}

	@Test
	public void testAddPasswordCredentials() throws IOException {
		P4PasswordImpl credential = new P4PasswordImpl(CredentialsScope.SYSTEM,
				"id", "description", "localhost:1666", null, "user", "0", "0",
				"pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertEquals("pass", credential.getPassword().getPlainText());
		assertFalse(credential.isSsl());

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
				"id", "description", "localhost:1666", ssl, "user", "0", "0", "pass");

		assertTrue(lookupCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().getCredentials()
				.add(credential);
		assertFalse(lookupCredentials().isEmpty());

		assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
		SystemCredentialsProvider.getInstance().save();
		assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());

		assertTrue(credential.isSsl());
		assertEquals("12345ABCD", credential.getTrust());
	}

	@Test
	public void testAddTicketCredentials() throws IOException {
		TicketModeImpl ticket = new TicketModeImpl("ticketValueSet", "12345",
				null);

		P4TicketImpl credential = new P4TicketImpl(CredentialsScope.SYSTEM,
				"id", "desc:ticket", "localhost:1666", null, "user", "0", "0",
				ticket);

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
		assertEquals("id (desc:ticket)", name);
	}

	@Test
	public void testAddTicketPathCredentials() throws IOException {
		TicketModeImpl ticket = new TicketModeImpl("ticketPathSet", null,
				"~/.p4ticket");

		P4TicketImpl credential = new P4TicketImpl(CredentialsScope.SYSTEM,
				"id", "description", "localhost:1666", null, "user", "0", "0",
				ticket);

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
		Jenkins scope = Jenkins.getInstance();
		Authentication acl = ACL.SYSTEM;
		DomainRequirement domain = new DomainRequirement();

		return CredentialsProvider.lookupCredentials(type, scope, acl, domain);
	}
}
