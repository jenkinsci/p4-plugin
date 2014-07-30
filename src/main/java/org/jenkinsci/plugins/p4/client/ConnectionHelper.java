package org.jenkinsci.plugins.p4.client;

import hudson.model.TaskListener;
import hudson.security.ACL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.server.CmdSpec;
import com.perforce.p4java.server.IOptionsServer;

public class ConnectionHelper {

	private static Logger logger = Logger.getLogger(ConnectionHelper.class
			.getName());

	protected final ConnectionConfig connectionConfig;
	protected final AuthorisationConfig authorisationConfig;
	protected IOptionsServer connection;
	protected final TaskListener listener;

	public ConnectionHelper(String credentialID, TaskListener listener) {
		this.listener = listener;
		P4StandardCredentials credential = findCredential(credentialID);
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connect();
	}

	public ConnectionHelper(P4StandardCredentials credential,
			TaskListener listener) {
		this.listener = listener;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connect();
	}

	public ConnectionHelper(P4StandardCredentials credential) {
		this.listener = null;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connect();
	}

	/**
	 * Convenience wrapper to connect and report errors
	 */
	private void connect() {
		// Connect to the Perforce server
		try {
			this.connection = ConnectionFactory.getConnection(connectionConfig);
			logger.fine("P4: opened connection OK");
		} catch (Exception e) {
			String err = "P4: Unable to connect: " + e;
			logger.severe(err);
			if (listener != null) {
				listener.getLogger().println(err);
			}
			e.printStackTrace();
		}
		
		// Login to Perforce
		try {
			login();
		} catch (Exception e) {
			String err = "P4: Unable to login: " + e;
			logger.severe(err);
			listener.getLogger().println(err);
			e.printStackTrace();
		}
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

		logger.info("P4: login failed '" + status + "'");
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
	 * Gets the Changelist (p4 describe -s); shouldn't need a client, but
	 * p4-java throws an exception if one is not set.
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public Changelist getChange(int id) throws Exception {
		return (Changelist) connection.getChangelist(id);
	}

	/**
	 * Get Perforce Label
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public Label getLabel(String id) throws Exception {
		return (Label) connection.getLabel(id);
	}

	/**
	 * Create/Update a Perforce Label
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public void setLabel(Label label) throws Exception {
		// connection.createLabel(label);
		String user = connection.getUserName();
		label.setOwnerName(user);
		connection.updateLabel(label);
	}

	/**
	 * Find all files within a label or change. (Max results limited by limit)
	 * 
	 * @param label
	 * @param limit
	 * @return
	 * @throws Exception
	 */
	public List<IFileSpec> getFiles(String id, int limit) throws Exception {
		String path = "//...@" + id;
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(path);
		GetDepotFilesOptions opts = new GetDepotFilesOptions();
		opts.setMaxResults(limit);
		List<IFileSpec> tagged = connection.getDepotFiles(spec, opts);
		return tagged;
	}

	/**
	 * Find all files within a shelf.
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public List<IFileSpec> loadShelvedFiles(int id) throws Exception {
		String cmd = CmdSpec.DESCRIBE.name();
		String[] args = new String[] { "-s", "-S", "" + id };
		List<Map<String, Object>> resultMaps;
		resultMaps = connection.execMapCmdList(cmd, args, null);

		List<IFileSpec> list = new ArrayList<IFileSpec>();

		if (resultMaps != null) {
			if ((resultMaps.size() > 0) && (resultMaps.get(0) != null)) {
				Map<String, Object> map = resultMaps.get(0);
				if (map.containsKey("shelved")) {
					for (int i = 0; map.get("rev" + i) != null; i++) {
						FileSpec fSpec = new FileSpec(map, connection, i);
						fSpec.setChangelistId(id);
						list.add(fSpec);
					}
				}
			}
		}
		return list;
	}

	/**
	 * Disconnect from the Perforce Server.
	 * 
	 * @throws Exception
	 */
	public void disconnect() {
		try {
			connection.disconnect();
			logger.fine("P4: closed connection OK");
		} catch (Exception e) {
			String err = "P4: Unable to close Perforce connection: " + e;
			logger.severe(err);
			if (listener != null) {
				listener.getLogger().println(err);
			}
			e.printStackTrace();
		}
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
