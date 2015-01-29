package org.jenkinsci.plugins.p4.workspace;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary.ClientLineEnd;
import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.impl.generic.client.ClientOptions;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.client.ClientView.ClientViewMapping;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.server.IOptionsServer;

public class ManualWorkspaceImpl extends Workspace {

	private final String name;
	public WorkspaceSpec spec;

	private static Logger logger = Logger.getLogger(ManualWorkspaceImpl.class
			.getName());

	@Override
	public String getName() {
		return name;
	}

	public WorkspaceSpec getSpec() {
		return spec;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.MANUAL;
	}

	@DataBoundConstructor
	public ManualWorkspaceImpl(String charset, boolean pinHost, String name,
			WorkspaceSpec spec) {
		super(charset, pinHost);
		this.name = name;
		this.spec = spec;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();

		IClient iclient = connection.getClient(clientName);
		if (iclient == null) {
			logger.info("P4: Creating manual client: " + clientName);
			Client implClient = new Client(connection);
			implClient.setName(clientName);
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
		}

		iclient.setOwnerName(user);

		ClientOptions options = new ClientOptions();
		options.setAllWrite(getSpec().allwrite);
		options.setClobber(getSpec().clobber);
		options.setCompress(getSpec().compress);
		options.setLocked(getSpec().locked);
		options.setModtime(getSpec().modtime);
		options.setRmdir(getSpec().rmdir);
		iclient.setOptions(options);

		iclient.setStream(getSpec().getStream());
		iclient.setLineEnd(parseLineEnd(getSpec().getLine()));

		ClientView clientView = new ClientView();
		int order = 0;
		for (String line : getSpec().getView().split("\\n")) {
			String origName = getName();
			line = line.replace(origName, clientName);
			line = expand(line, false);

			try {
				ClientViewMapping entry = new ClientViewMapping(order, line);
				order++;
				clientView.addEntry(entry);
			} catch (Exception e) {
				String msg = "P4: invalid client view: " + line;
				logger.warning(msg);
				throw new AbortException(msg);
			}
		}
		iclient.setClientView(clientView);

		return iclient;
	}

	private ClientLineEnd parseLineEnd(String line) {
		for (ClientLineEnd end : ClientLineEnd.values()) {
			end.name().equalsIgnoreCase(line);
			return end;
		}
		return ClientLineEnd.LOCAL;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Manual (custom view)";
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 * 
		 * @param value
		 *            The text that the user entered.
		 */
		public AutoCompletionCandidates doAutoCompleteName(
				@QueryParameter String value) {
			return autoCompleteName(value);
		}

		public FormValidation doCheckName(@QueryParameter String value) {
			return checkClientName(value);
		}
	}

	@JavaScriptMethod
	public JSONObject getSpecJSON(String client) {
		try {
			IOptionsServer p4 = ConnectionFactory.getConnection();
			IClient c = p4.getClient(client);

			StringBuffer sb = new StringBuffer();
			for (IClientViewMapping view : c.getClientView()) {
				sb.append(view.toString(" ", true));
				sb.append("\n");
			}

			JSONObject option = new JSONObject();
			option.put("allwrite", c.getOptions().isAllWrite());
			option.put("clobber", c.getOptions().isClobber());
			option.put("compress", c.getOptions().isCompress());
			option.put("locked", c.getOptions().isLocked());
			option.put("modtime", c.getOptions().isModtime());
			option.put("rmdir", c.getOptions().isRmdir());

			JSONObject spec = new JSONObject();
			spec.put("stream", (c.getStream() == null) ? "" : c.getStream());
			spec.put("line", c.getLineEnd().name());
			spec.put("view", sb.toString());
			spec.put("options", option);
			return spec;

		} catch (Exception e) {
			JSONObject option = new JSONObject();
			option.put("allwrite", false);
			option.put("clobber", true);
			option.put("compress", false);
			option.put("locked", false);
			option.put("modtime", false);
			option.put("rmdir", false);

			JSONObject spec = new JSONObject();
			spec.put("stream", "");
			spec.put("line", "LOCAL");
			spec.put("view", "please define view...");
			spec.put("options", option);
			return spec;
		}
	}
}
