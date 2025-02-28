package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.SwitchClientViewOptions;
import com.perforce.p4java.server.IOptionsServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Logger;

public class TemplateWorkspaceImpl extends Workspace implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String templateName;
	private String format;

	private static final Logger logger = Logger.getLogger(TemplateWorkspaceImpl.class
			.getName());

	public String getTemplateName() {
		return templateName;
	}

	public String getFormat() {
		return format;
	}

	@Override
	public String getName() {
		return format;
	}

	@Override
	public void setName(String name) {
		this.format = name;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.TEMPLATED;
	}

	@DataBoundConstructor
	public TemplateWorkspaceImpl(String charset, boolean pinHost,
								 String templateName, String format) {
		super(charset, pinHost, false);
		this.templateName = templateName;
		this.format = format;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {

		String template = getTemplateClient();

		// Check template exists or exit early
		IClient itemplate = connection.getClient(template);
		if (itemplate == null) {
			return null;
		}

		// expands Workspace name if formatters are used.
		String clientName = getFullName();

		IClient iclient = connection.getClient(clientName);
		if (iclient == null) {
			logger.info("P4: Creating template client: " + clientName);
			Client implClient = new Client(connection);
			implClient.setName(clientName);
			implClient.setOwnerName(user);
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
			// Root required to switch view; must reload values in iclient.
			iclient.setRoot(getRootPath());
		}

		// Owner set for use with p4maven
		iclient.setOwnerName(user);

		// set line endings explicitly (JENKINS-28760)
		iclient.setLineEnd(itemplate.getLineEnd());

		// Clear lock flag (JENKINS-55826)
		IClientSummary.IClientOptions clientOpts = itemplate.getOptions();
		clientOpts.setLocked(false);

		// set options explicitly (JENKINS-30546)
		iclient.setOptions(clientOpts);

		iclient.update();

		// Use template with client
		SwitchClientViewOptions opts = new SwitchClientViewOptions();
		opts.setForce(true);
		connection.switchClientView(template, clientName, opts);
		iclient = connection.getClient(clientName);
		return iclient;
	}

	private String getTemplateClient() {
		// Expand env variables in Template name
		Expand expand = getExpand();
		return expand.format(getTemplateName(), false);
	}

	public boolean templateExists(IOptionsServer connection) throws Exception {
		String template = getTemplateClient();
		return (connection.getClient(template) != null);
	}

	@Extension
	@Symbol("templateSpec")
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		@NonNull
		public String getDisplayName() {
			return "Template (view generated for each node)";
		}

		public FormValidation doCheckTemplateName(@QueryParameter String value) {
			return checkClientName(value);
		}
	}
}
