package org.jenkinsci.plugins.p4.client;

import hudson.model.TaskListener;
import hudson.security.ACL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.server.IOptionsServer;

public class ConnectionHelper {

	private static Logger logger = Logger.getLogger(ConnectionHelper.class
			.getName());

	protected final ConnectionConfig connectionConfig;
	protected final AuthorisationConfig authorisationConfig;
	protected final IOptionsServer connection;
	private final TaskListener listener;

	public ConnectionHelper(String credentialID, TaskListener listener)
			throws Exception {
		this.listener = listener;
		P4StandardCredentials credential = findCredential(credentialID);
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		this.connection = ConnectionFactory.getConnection(connectionConfig);
	}

	public ConnectionHelper(P4StandardCredentials credential,
			TaskListener listener) throws Exception {
		this.listener = listener;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		this.connection = ConnectionFactory.getConnection(connectionConfig);
	}

	public ConnectionHelper(P4StandardCredentials credential) throws Exception {
		this.listener = null;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		this.connection = ConnectionFactory.getConnection(connectionConfig);
	}

	public boolean isConnected() {
		return connection.isConnected();
	}

	public boolean isUnicode() {
		try {
			return connection.getServerInfo().isUnicodeEnabled();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Checks the Perforce server version number and returns true if greater
	 * than or equal to the min version. The value of min must be of the form
	 * 20092 or 20073 (corresponding to 2009.2 and 2007.3 respectively).
	 * 
	 * @param min
	 * @return
	 */
	public boolean checkVersion(int min) {
		int ver = connection.getServerVersionNumber();
		return (ver >= min);
	}

	public boolean login() throws Exception {
		connection.setUserName(authorisationConfig.getUsername());

		// CHARSET is not defined (only for client access)
		if (connection.getServerInfo().isUnicodeEnabled()) {
			connection.setCharsetName("utf8");
		}

		switch (authorisationConfig.getType()) {
		case PASSWORD:
			String status = connection.getLoginStatus();
			if (!status.contains("not necessary")) {
				String pass = authorisationConfig.getPassword();
				connection.login(pass);
			}
			break;

		case TICKET:
			String ticket = authorisationConfig.getTicketValue();
			connection.setAuthTicket(ticket);
			break;

		case TICKETPATH:
			String path = authorisationConfig.getTicketPath();
			connection.setTicketsFilePath(path);
			break;

		default:
			throw new Exception("Unknown Authorisation type: "
					+ authorisationConfig.getType());
		}

		return isLogin();
	}

	public void logout() throws Exception {
		if (isLogin()) {
			connection.logout();
		}
	}

	private boolean isLogin() throws Exception {
		String status = connection.getLoginStatus();
		if (status.contains("not necessary")) {
			return true;
		}
		if (status.contains("ticket expires in")) {
			return true;
		}
		// If there is a broker or something else that swallows the message
		if (status.isEmpty()) {
			return true;
		}

		logger.info("P4:login failed '" + status + "'");
		return false;
	}

	public void deleteClient(Workspace workspace) throws Exception {
		if (connectionConfig.isUnicode()) {
			String charset = "utf8";
			connection.setCharsetName(charset);
		}

		String name = workspace.getFullName();
		connection.deleteClient(name, true);
	}

	/**
	 * Disconnect from the Perforce Server.
	 * 
	 * @throws Exception
	 */
	public void disconnect() throws Exception {
		connection.disconnect();
	}

	/**
	 * Look for a message in the returned FileSpec from operation.
	 * 
	 * @param fileSpecs
	 * @param ignore
	 * @return
	 * @throws ConverterException
	 */
	public boolean validateFileSpecs(List<IFileSpec> fileSpecs,
			String... ignore) throws Exception {
		return validateFileSpecs(fileSpecs, false, ignore);
	}

	public boolean validateFileSpecs(List<IFileSpec> fileSpecs, boolean quiet,
			String... ignore) throws Exception {
		for (IFileSpec fileSpec : fileSpecs) {
			if (fileSpec.getOpStatus() != FileSpecOpStatus.VALID) {
				String msg = fileSpec.getStatusMessage();

				// superfluous p4java message
				boolean unknownMsg = true;
				ArrayList<String> ignoreList = new ArrayList<String>();
				ignoreList.addAll(Arrays.asList(ignore));
				for (String istring : ignoreList) {
					if (msg.contains(istring)) {
						// its a known message
						unknownMsg = false;
					}
				}

				// check and report unknown message
				if (unknownMsg) {
					if (!quiet) {
						log("P4JAVA: " + msg);
						logger.warning("p4java: " + msg);
					}
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Finds a Perforce Credential based on the String id.
	 * 
	 * @return a P4StandardCredentials credential or null if not found.
	 */
	public static P4StandardCredentials findCredential(String id) {
		Class<P4StandardCredentials> type = P4StandardCredentials.class;
		Jenkins scope = Jenkins.getInstance();
		Authentication acl = ACL.SYSTEM;
		DomainRequirement domain = new DomainRequirement();

		List<P4StandardCredentials> list;
		list = CredentialsProvider.lookupCredentials(type, scope, acl, domain);

		for (P4StandardCredentials c : list) {
			if (c.getId().equals(id)) {
				return c;
			}
		}
		return null;
	}

	protected void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}

	public void stop() throws Exception {
		connection.execMapCmd("admin", new String[] { "stop" }, null);
	}

}
