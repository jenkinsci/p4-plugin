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
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.Serializable;
import java.util.logging.Logger;

public class ManualWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String name;
	public WorkspaceSpec spec;

	private static Logger logger = Logger.getLogger(ManualWorkspaceImpl.class.getName());

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
	public ManualWorkspaceImpl(String charset, boolean pinHost, String name, WorkspaceSpec spec) {
		super(charset, pinHost);
		this.name = name;
		this.spec = spec;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user) throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();

		IClient iclient = connection.getClient(clientName);
		if (iclient == null) {
			logger.info("P4: Creating manual client: " + clientName);
			Client implClient = new Client(connection);
			implClient.setName(clientName);
			implClient.setOwnerName(user);
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
		}

		// Owner set for use with p4maven
		iclient.setOwnerName(user);

		ClientOptions options = new ClientOptions();
		options.setAllWrite(getSpec().allwrite);
		options.setClobber(getSpec().clobber);
		options.setCompress(getSpec().compress);
		options.setLocked(getSpec().locked);
		options.setModtime(getSpec().modtime);
		options.setRmdir(getSpec().rmdir);
		iclient.setOptions(options);

		// Expand Stream name
		String streamFullName = getSpec().getStreamName();
		if (streamFullName != null) {
			streamFullName = getExpand().format(streamFullName, false);
		}
		iclient.setStream(streamFullName);

		iclient.setLineEnd(parseLineEnd(getSpec().getLine()));

		ClientView clientView = new ClientView();
		int order = 0;
		for (String line : getSpec().getView().split("\\n")) {
			String origName = getName();
			line = line.replace(origName, clientName);
			line = getExpand().format(line, false);

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
			if (end.name().equalsIgnoreCase(line)) {
				return end;
			}
		}
		return ClientLineEnd.LOCAL;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

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
