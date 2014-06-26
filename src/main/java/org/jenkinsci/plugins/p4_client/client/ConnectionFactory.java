package org.jenkinsci.plugins.p4_client.client;

import java.util.Properties;
import java.util.logging.Logger;

import com.perforce.p4java.PropertyDefs;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;

/**
 * Connection Factory
 * 
 * Provides concurrent connections to the Perforce Server
 * 
 * @author pallen
 * 
 */
public class ConnectionFactory {

	private static Logger logger = Logger.getLogger(ConnectionFactory.class
			.getName());

	private static IOptionsServer currentP4;

	/**
	 * Returns existing connection
	 * 
	 * @return
	 */
	public static IOptionsServer getConnection() {
		return currentP4;
	}

	/**
	 * Creates a server connection; provides a connection to the Perforce
	 * Server, initially client is undefined.
	 * 
	 * @return
	 * @throws Exception
	 */
	public static IOptionsServer getConnection(ConnectionConfig config)
			throws Exception {

		Properties props = System.getProperties();

		// Identify ourselves in server log files.
		Identifier id = new Identifier();
		props.put(PropertyDefs.PROG_NAME_KEY, id.getProduct());
		props.put(PropertyDefs.PROG_VERSION_KEY, id.getVersion());

		// Allow p4 admin commands.
		props.put(RpcPropertyDefs.RPC_RELAX_CMD_NAME_CHECKS_NICK, "true");

		// disable timeout for slow servers / large db lock times
		props.put(RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK, "0");

		// Get a server connection
		String serverUri = config.getServerUri();
		IOptionsServer iserver;
		iserver = ServerFactory.getOptionsServer(serverUri, props);

		// Add trust for SSL connections
		if (config.isSsl()) {
			iserver.addTrust(config.getTrust());
		}

		// Connect and update current P4 connection
		iserver.connect();
		currentP4 = iserver;
		return iserver;
	}
}