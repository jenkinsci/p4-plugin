package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.util.Collections;
import java.util.List;

public class CredentialsHelper {

	private final TaskListener listener;
	private final P4BaseCredentials credential;
	private final AuthorisationConfig authorisationConfig;

	public CredentialsHelper(P4BaseCredentials credential, TaskListener listener) {
		this.credential = credential;
		this.listener = listener;

		this.authorisationConfig = new AuthorisationConfig(getCredential());
	}

	public CredentialsHelper(String credentialID, TaskListener listener) {
		this.credential = findCredential(credentialID);
		this.listener = listener;

		this.authorisationConfig = new AuthorisationConfig(getCredential());
	}


	public TaskListener getListener() {
		return listener;
	}

	public P4BaseCredentials getCredential() {
		return credential;
	}

	public AuthorisationConfig getAuthorisationConfig() {
		return authorisationConfig;
	}

	public void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}

	public int getRetry() {
		return credential.getRetry();
	}

	public int getTick() {
		return credential.getTick();
	}

	public String getUser() {
		return credential.getUsername();
	}

	public String getPort() {
		return credential.getFullP4port();
	}

	/**
	 * Finds a Perforce Credential based on the String id.
	 *
	 * @param id Credential ID
	 * @return a P4StandardCredentials credential or null if not found.
	 * @deprecated Use {@link #findCredential(String, ItemGroup)} or {@link #findCredential(String, Item)}
	 */
	@Deprecated
	public static P4BaseCredentials findCredential(String id) {
		Class<P4BaseCredentials> type = P4BaseCredentials.class;
		Jenkins scope = Jenkins.get();
		Authentication acl = ACL.SYSTEM;
		DomainRequirement domain = new DomainRequirement();

		List<P4BaseCredentials> list;
		list = CredentialsProvider.lookupCredentials(type, scope, acl, domain);

		for (P4BaseCredentials c : list) {
			if (c.getId().equals(id)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Finds a Perforce Credential based on the String id.
	 *
	 * @param credentialsId Credential ID
	 * @param context       The context
	 * @return a P4StandardCredentials credential or null if not found.
	 */
	public static P4BaseCredentials findCredential(String credentialsId, ItemGroup context) {
		if (credentialsId == null) {
			return null;
		}
		P4BaseCredentials credentials = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentialsInItemGroup(P4BaseCredentials.class, context,
						ACL.SYSTEM2, Collections.emptyList()),
				CredentialsMatchers.allOf(
						CredentialsMatchers.withId(credentialsId),
						CredentialsMatchers.instanceOf(P4BaseCredentials.class)));
		return credentials;
	}

	/**
	 * Finds a Perforce Credential based on credentials ID and {@link Item}.
	 * This also tracks usage of the credentials.
	 *
	 * @param credentialsId Credential ID
	 * @param item          The {@link Item}
	 * @return a P4StandardCredentials credential or null if not found.
	 */
	public static P4BaseCredentials findCredential(String credentialsId, Item item) {
		if (credentialsId == null) {
			return null;
		}
		if (item == null) {
			return findCredential(credentialsId);
		}
		P4BaseCredentials credentials = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentialsInItem(P4BaseCredentials.class, item,
						ACL.SYSTEM2, Collections.emptyList()),
				CredentialsMatchers.allOf(
						CredentialsMatchers.withId(credentialsId),
						CredentialsMatchers.instanceOf(P4BaseCredentials.class)));
		return credentials;
	}

	/**
	 * Finds a Perforce Credential based on the String id and {@link Run}.
	 *
	 * @param credentialsId Credential ID
	 * @param run           The {@link Run}
	 * @return a P4StandardCredentials credential or null if not found.
	 */
	public static P4BaseCredentials findCredential(String credentialsId, Run run) {
		if (credentialsId == null) {
			return null;
		}
		P4BaseCredentials credentials = CredentialsProvider.findCredentialById(credentialsId,
				P4BaseCredentials.class, run, Collections.emptyList());
		return credentials;
	}
}
