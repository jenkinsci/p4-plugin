package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary.ClientLineEnd;
import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.client.ClientOptions;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.client.ClientView.ClientViewMapping;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManualWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;
	public WorkspaceSpec spec;

	private static final Logger logger = Logger.getLogger(ManualWorkspaceImpl.class.getName());

	private static final String InitialViewExpandKey = "P4_INITIAL_VIEW";

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
	public ManualWorkspaceImpl(String charset, boolean pinHost, String name, WorkspaceSpec spec, boolean cleanup) {
		super(charset, pinHost, cleanup);
		this.name = name;
		this.spec = spec;
	}

	@Deprecated
	public ManualWorkspaceImpl(String charset, boolean pinHost, String name, WorkspaceSpec spec) {
		super(charset, pinHost, false);
		this.name = name;
		this.spec = spec;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		ManualWorkspaceImpl ws = (ManualWorkspaceImpl) super.clone();
		ws.spec = (WorkspaceSpec) spec.clone();
		return ws;
	}

	private ClientView getClientView(IOptionsServer connection, WorkspaceSpec workspaceSpec) throws Exception {
		String clientName = getFullName();
		String specString = getExpand().format(workspaceSpec.getView(), true);

		if (specString.startsWith("@")) {
			// retrieve view from depot file and construct the view.
			logger.fine("P4: specString=" + specString);
			String specPathFull = specString.substring(1).trim();
			List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(specPathFull);
			GetFileContentsOptions printOpts = new GetFileContentsOptions();
			printOpts.setNoHeaderLine(true);
			InputStream ins = connection.getFileContents(file, printOpts);
			String spec;
			try {
				spec = IOUtils.toString(ins, "UTF-8");
			} finally {
				ins.close();
			}
			spec = getExpand().format(spec, true);

			specString = cleanupViewFromFile(spec, clientName);
		}

		// Split on new line and trim any following white space
		int order = 0;
		ClientView clientView = new ClientView();
		for (String line : specString.split("\n\\s*")) {
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

	// for matching the depot/clients paths in view line.
	private static final Pattern PAT_DETECT = Pattern.compile("//([^/]*)/");

	/**
	 * Look at each line and substitute the clientname in the RHS.
	 * If there is only a LHS, construct a RHS. so:
	 * <pre>//depot/path/to/product/...</pre>
	 * becomes:
	 * <pre>//depot/path/to/product/... //CLIENT/path/to/product/...</pre>
	 * @param spec line(s) of view.
	 * @param clientName client name for RHS
	 * @return
	 */
	public String cleanupViewFromFile(String spec, String clientName) {
		logger.fine("P4: specFromFile=" + spec);
		spec = getExpand().format(spec, true);

		StringBuilder sb = new StringBuilder(100);
		// substitute for clientName or construct RHS
		for (String line : spec.split("\n\\s*")) {
			Matcher matcher = PAT_DETECT.matcher(line);  // first found is depot name, optional second is clientname
			String depot = null, client = null;
			if (matcher.find()) {
				depot = matcher.group(1);
				if (matcher.find()) {
					client = matcher.group(1);
				}
			}
			if (depot != null && client == null) {
				// compose RHS.
				line = ViewMapHelper.getClientView(Arrays.asList(new String[]{line}), clientName, true, false);
				// getClientView() adds the depot to the RHS so remove it here.
				line = line.replace("//" + clientName + "/" + depot, "//" + clientName );
			} else if (client != null) {
				line = line.replace("//" + client, "//" + clientName );
			}
			if (sb.length() != 0) {
				sb.append("\n");
			}
			sb.append(line);
		}

		logger.fine("P4: specReplaced=" + sb);
		return sb.toString();
	}

	private ArrayList<String> getChangeView(WorkspaceSpec workspaceSpec) {
		ArrayList<String> changeView = new ArrayList<>();

		String view = workspaceSpec.getChangeView();
		if (view != null) {
			String specString = getExpand().format(view, true);
			for (String line : specString.split("\\n")) {
				changeView.add(line);
			}
		}
		return changeView;
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

			if (connection.getServerVersionNumber() >= 20162) {
				WorkspaceSpecType type = parseClientType(getSpec().getType());
				if (connection.getServerVersionNumber() >= type.getMinVersion()) {
					iclient.setType(type.getId());
				}
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
			streamFullName = getExpand().format(streamFullName, true);
		}
		iclient.setStream(streamFullName);

		// Set Client view
		iclient.setClientView(getClientView(connection, getSpec()));

		// Set Change view
		if (connection.getServerVersionNumber() >= 20172) {
			iclient.setChangeView(getChangeView(getSpec()));
		}

		// Allow change between GRAPH and WRITEABLE
		if (connection.getServerVersionNumber() >= 20162) {
			WorkspaceSpecType type = parseClientType(getSpec().getType());
			if (connection.getServerVersionNumber() >= type.getMinVersion()) {
				switch (type) {
					case GRAPH:
					case WRITABLE:
						iclient.setType(type.getId());
						break;
					default:
				}
			}
		}

		iclient.setServerId(getSpec().getServerID());

		// Set Backup option, defaults to enable (enable/disable)
		if (connection.getServerVersionNumber() >= 20172) {
			if (getSpec().isBackup()) {
				iclient.setBackup("enable");
			} else {
				iclient.setBackup("disable");
			}
		}

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
