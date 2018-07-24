package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary.ClientLineEnd;
import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.client.ClientOptions;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.client.ClientView.ClientViewMapping;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.server.IOptionsServer;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.Serializable;
import java.util.logging.Logger;

public class ManualWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;
	public WorkspaceSpec spec;

	private static Logger logger = Logger.getLogger(ManualWorkspaceImpl.class.getName());

	private static String InitialViewExpandKey = "P4_INITIAL_VIEW";

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	public WorkspaceSpec getSpec() {
		return spec;
	}

	public void setSpec(WorkspaceSpec spec) {
		this.spec = spec;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.MANUAL;
	}

	@DataBoundConstructor
	public ManualWorkspaceImpl(String charset, boolean pinHost, String name, WorkspaceSpec spec) {
		super(charset, pinHost);
		this.name = name;
		this.spec = spec;
	}

	private ClientView getClientView(WorkspaceSpec workspaceSpec) throws Exception {
		String clientName = getFullName();
		ClientView clientView = new ClientView();
		int order = 0;
		String specString = getExpand().format(workspaceSpec.getView(), false);
		for (String line : specString.split("\\n")) {
			String origName = getExpand().format(getName(), false);
			line = line.replace(origName, clientName);

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
		return clientView;
	}


	private ClientOptions getClientOptions(WorkspaceSpec spec) {
		ClientOptions options = new ClientOptions();
		options.setAllWrite(spec.allwrite);
		options.setClobber(spec.clobber);
		options.setCompress(spec.compress);
		options.setLocked(spec.locked);
		options.setModtime(spec.modtime);
		options.setRmdir(spec.rmdir);
		return options;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user) throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();

		IClient iclient = connection.getClient(clientName);

		// If a new client then initialise
		if (iclient == null) {
			logger.info("P4: Creating manual client: " + clientName);
			iclient = new Client(connection);
			iclient.setName(clientName);
			iclient.setOwnerName(user);
			iclient.setRoot(getRootPath());

			if (connection.getServerVersionNumber() >= 20171) {
				WorkspaceSpecType type = parseClientType(getSpec().getType());
				iclient.setType(type.getId());
			}
		}

		// Owner set for use with p4maven
		iclient.setOwnerName(user);

		// Set options
		iclient.setOptions(getClientOptions(getSpec()));
		iclient.setLineEnd(parseLineEnd(getSpec().getLine()));

		// Expand Stream name
		String streamFullName = getSpec().getStreamName();
		if (streamFullName != null) {
			streamFullName = getExpand().format(streamFullName, false);
		}
		iclient.setStream(streamFullName);

		// Set Client view
		iclient.setClientView(getClientView(getSpec()));

		// Set Change view
		// TODO (p4java 17.2) changeView

		// Allow change between GRAPH and WRITEABLE
		if (connection.getServerVersionNumber() >= 20171) {
			WorkspaceSpecType type = parseClientType(getSpec().getType());
			switch (type) {
				case GRAPH:
				case WRITABLE:
					iclient.setType(type.getId());
					break;
				default:
			}
		}

		iclient.setServerId(getSpec().getServerID());

		// TODO (p4java 17.2) backup

		// Save new/updated changes
	//	connection.createClient(iclient);
	//	iclient = connection.getClient(clientName);

		return iclient;
	}

	private ClientLineEnd parseLineEnd(String line) {
		for (ClientLineEnd end : ClientLineEnd.values()) {
			if (end.name().equalsIgnoreCase(line)) {
				return end;
			}
		}
		return ClientLineEnd.LOCAL;
	}

	private WorkspaceSpecType parseClientType(String line) {
		for (WorkspaceSpecType type : WorkspaceSpecType.values()) {
			if (type.name().equalsIgnoreCase(line)) {
				return type;
			}
		}
		return WorkspaceSpecType.WRITABLE;
	}

	@Extension
	@Symbol("manualSpec")
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Manual (custom view)";
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 *
		 * @param value The text that the user entered.
		 * @return suggestion
		 */
		public AutoCompletionCandidates doAutoCompleteName(@QueryParameter String value) {
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
		} catch (P4JavaException e) {
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
