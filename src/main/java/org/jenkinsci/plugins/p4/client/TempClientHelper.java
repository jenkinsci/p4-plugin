package org.jenkinsci.plugins.p4.client;

import hudson.model.Item;
import hudson.model.TaskListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TempClientHelper extends ClientHelper implements Closeable {

	private static final Logger LOGGER = Logger.getLogger(TempClientHelper.class.getName());

	private final String clientUUID;

	public TempClientHelper(Item context, String credential, TaskListener listener, String charset) {
		super(context, credential, listener);
		this.clientUUID = "jenkinsTemp-" + UUID.randomUUID().toString();
		clientLogin(clientUUID, charset);
	}

	@Override
	public void close() throws IOException {
		try {
			deleteClient(clientUUID);
		} catch (Exception e) {
			LOGGER.log(Level.INFO, "Unable to remove temporary client: " + clientUUID);
		}
		disconnect();
	}
}
