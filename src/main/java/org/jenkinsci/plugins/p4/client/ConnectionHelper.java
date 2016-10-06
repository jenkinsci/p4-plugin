package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.perforce.p4java.admin.IProperty;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.ILabel;
import com.perforce.p4java.core.IUser;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.option.server.ChangelistOptions;
import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.option.server.DeleteClientOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.option.server.GetFixesOptions;
import com.perforce.p4java.option.server.GetPropertyOptions;
import com.perforce.p4java.server.CmdSpec;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.callback.ICommandCallback;
import com.perforce.p4java.server.callback.IProgressCallback;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.console.P4Logging;
import org.jenkinsci.plugins.p4.console.P4Progress;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHelper {

	private static Logger logger = Logger.getLogger(ConnectionHelper.class.getName());

	private boolean abort = false;

	protected final ConnectionConfig connectionConfig;
	protected final AuthorisationConfig authorisationConfig;
	protected IOptionsServer connection;
	protected final TaskListener listener;
	protected final P4BaseCredentials p4credential;

	public ConnectionHelper(String credentialID, TaskListener listener) {
		this.listener = listener;
		P4BaseCredentials credential = findCredential(credentialID);
		this.p4credential = credential;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connectionRetry();
	}

	public ConnectionHelper(P4BaseCredentials credential, TaskListener listener) {
		this.listener = listener;
		this.p4credential = credential;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connectionRetry();
	}

	public ConnectionHelper(P4BaseCredentials credential) {
		this.listener = new LogTaskListener(logger, Level.INFO);
		this.p4credential = credential;
		this.connectionConfig = new ConnectionConfig(credential);
		this.authorisationConfig = new AuthorisationConfig(credential);
		connectionRetry();
	}
	
	public IOptionsServer getConnection() {
		return connection;
	}

	/**
	 * Convenience wrapper to connect and report errors
	 */
	private boolean connect() {
		// Connect to the Perforce server
		try {
			this.connection = ConnectionFactory.getConnection(connectionConfig);
			logger.fine("P4: opened connection OK");
		} catch (Exception e) {
			String err = "P4: Unable to connect: " + e;
			logger.severe(err);
			log(err);
			return false;
		}

		// Login to Perforce
		try {
			login();
		} catch (Exception e) {
			String err = "P4: Unable to login: " + e;
			logger.severe(err);
			log(err);
			return false;
		}

		// Register progress callback
		IProgressCallback progress = new P4Progress(listener, this);
		this.connection.registerProgressCallback(progress);

		// Register logging callback
		ICommandCallback logging = new P4Logging(listener);
		this.connection.registerCallback(logging);

		// Get Environment
		String ignore = ".p4ignore";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			ignore = "p4ignore.txt";
		}

		// Set p4ignore file
		Server server = (Server) this.connection;
		server.setIgnoreFileName(ignore);

		return true;
	}

	/**
	 * Retry Connection with back off for each failed attempt.
	 */
	private void connectionRetry() {
		int trys = 0;
		int attempt = getRetry();
		while (trys <= attempt) {
			if (connect()) {
				return;
			}
			trys++;
			String err = "P4: Connection retry: " + trys;
			logger.severe(err);
			log(err);

			// back off n^2 seconds, before retry
			try {
				TimeUnit.SECONDS.sleep(trys ^ 2);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		String err = "P4: Connection retry giving up...";
		logger.severe(err);
		log(err);
	}

	public int getRetry() {
		return p4credential.getRetry();
	}

	public String getPort() {
		return p4credential.getP4port();
	}

	public String getTrust() throws Exception {
		return connection.getTrust();
	}

	public String getTicket() {
		try {
			if (login()) {
				return connection.getAuthTicket();
			}
		} catch (Exception e) {
		}
		return null;
	}

	public boolean isConnected() {
		if (connection == null) {
			return false;
		}
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
	 * @param min Minimum server version
	 * @return true if version supported.
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
				if (!isLogin()) {
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
				throw new Exception("Unknown Authorisation type: " + authorisationConfig.getType());
		}

		// return login status...
		if (isLogin()) {
			return true;
		} else {
			String status = connection.getLoginStatus();
			logger.info("P4: login failed '" + status + "'");
			return false;
		}
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
		return false;
	}

	/**
	 * Gets the Changelist (p4 describe -s); shouldn't need a client, but
	 * p4-java throws an exception if one is not set.
	 *
	 * @param id Change number (long perhaps)
	 * @return Perforce Changelist
	 * @throws Exception push up stack
	 */
	public Changelist getChange(int id) throws Exception {
		try {
			return (Changelist) connection.getChangelist(id);
		} catch (RequestException e) {
			ChangelistOptions opts = new ChangelistOptions();
			opts.setOriginalChangelist(true);
			return (Changelist) connection.getChangelist(id, opts);
		}
	}

	public IChangelistSummary getChangeSummary(int id) throws P4JavaException {
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList("@" + id);
		GetChangelistsOptions cngOpts = new GetChangelistsOptions();
		cngOpts.setLongDesc(true);
		cngOpts.setMaxMostRecent(1);
		List<IChangelistSummary> summary = connection.getChangelists(spec, cngOpts);
		if (summary.isEmpty()) {
			return null;
		}
		return summary.get(0);
	}

	public List<IFix> getJobs(int id) throws P4JavaException {
		GetFixesOptions opts = new GetFixesOptions();
		opts.setChangelistId(id);
		List<IFix> fixes = connection.getFixes(null, opts);
		return fixes;
	}

	/**
	 * Test if given name is a counter
	 *
	 * @param name Couner name
	 * @return true if counter
	 * @throws Exception push up stack
	 */
	public boolean isCounter(String name) throws Exception {
		if (name.equals("now")) {
			return false;
		}
		try {
			CounterOptions opts = new CounterOptions();
			String counter = connection.getCounter(name, opts);
			return (!"0".equals(counter));
		} catch (RequestException e) {
			return false;
		}
	}

	/**
	 * Get Perforce Counter
	 *
	 * @param id Counter name
	 * @return Perforce Counter
	 * @throws Exception push up stack
	 */
	public String getCounter(String id) throws Exception {
		CounterOptions opts = new CounterOptions();
		String counter = connection.getCounter(id, opts);
		return counter;
	}

	/**
	 * Test if given name is a label
	 *
	 * @param name Label name
	 * @return true if label.
	 * @throws Exception push up stack
	 */
	public boolean isLabel(String name) throws Exception {
		if (name.equals("now")) {
			return false;
		}
		try {
			ILabel label = connection.getLabel(name);
			return (label != null);
		} catch (RequestException e) {
			return false;
		}
	}

	/**
	 * Test if given name is a client
	 *
	 * @param name Client name
	 * @return true if client exists.
	 * @throws Exception push up stack
	 */
	public boolean isClient(String name) throws Exception {
		try {
			if (name == null) {
				return false;
			}
			IClient client = connection.getClient(name);
			return (client != null);
		} catch (RequestException e) {
			return false;
		}
	}

	/**
	 * Delete a client workspace
	 *
	 * @param name Client name
	 * @throws Exception push up stack
	 */
	public void deleteClient(String name) throws Exception {
		DeleteClientOptions opts = new DeleteClientOptions();
		connection.deleteClient(name, opts);
	}

	public String getEmail(String userName) throws Exception {
		IUser user = connection.getUser(userName);
		if (user != null) {
			String email = user.getEmail();
			return email;
		}
		return "";
	}

	/**
	 * Get Perforce Label
	 *
	 * @param id Label name
	 * @return Perforce Label
	 * @throws Exception push up stack
	 */
	public Label getLabel(String id) throws Exception {
		return (Label) connection.getLabel(id);
	}

	/**
	 * Create/Update a Perforce Label
	 *
	 * @param label Label name
	 * @throws Exception push up stack
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
	 * @param id    Label name or change number (as string)
	 * @param limit Max results (-m value)
	 * @return List of file specs
	 * @throws Exception push up stack
	 */
	public List<IFileSpec> getLabelFiles(String id, int limit) throws Exception {
		String path = "//...@" + id;
		List<IFileSpec> spec = FileSpecBuilder.makeFileSpecList(path);
		GetDepotFilesOptions opts = new GetDepotFilesOptions();
		opts.setMaxResults(limit);

		List<IFileSpec> tagged = connection.getDepotFiles(spec, opts);
		return tagged;
	}

	// Use a describe for files to avoid MAXSCANROW limits.
	// (backed-out part of change 16390)
	public List<IFileSpec> getChangeFiles(int id) throws Exception {
		List<IFileSpec> files = connection.getChangelistFiles(id);
		return files;
	}

	/**
	 * Find all files within a shelf.
	 *
	 * @param id Shelf ID
	 * @return List of file specs
	 * @throws Exception push up stack
	 */
	public List<IFileSpec> getShelvedFiles(int id) throws Exception {
		String cmd = CmdSpec.DESCRIBE.name();
		String[] args = new String[]{"-s", "-S", "" + id};
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

	public String getSwarm() throws P4JavaException {
		GetPropertyOptions propOpts = new GetPropertyOptions();
		String key = "P4.Swarm.URL";
		propOpts.setName(key);
		List<IProperty> values = connection.getProperty(propOpts);
		for (IProperty prop : values) {
			if (key.equals(prop.getName())) {
				return prop.getValue();
			}
		}
		return null;
	}

	/**
	 * Disconnect from the Perforce Server.
	 */
	public void disconnect() {
		try {
			connection.disconnect();
			logger.fine("P4: closed connection OK");
		} catch (Exception e) {
			String err = "P4: Unable to close Perforce connection.";
			logger.severe(err);
			log(err);
		}
	}

	/**
	 * Finds a Perforce Credential based on the String id.
	 *
	 * @param id Credential ID
	 * @return a P4StandardCredentials credential or null if not found.
	 */
	public static P4BaseCredentials findCredential(String id) {
		Class<P4BaseCredentials> type = P4BaseCredentials.class;
		Jenkins scope = Jenkins.getInstance();
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

	public void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}

	public void stop() throws Exception {
		connection.execMapCmd("admin", new String[]{"stop"}, null);
	}

	public boolean hasAborted() {
		return abort;
	}

	public void abort() {
		this.abort = true;
	}
}
