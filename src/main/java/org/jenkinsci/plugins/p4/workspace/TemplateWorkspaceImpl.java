package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.SwitchClientViewOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.Extension;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;
import java.util.logging.Logger;

public class TemplateWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String templateName;
	private String format;

	private static Logger logger = Logger.getLogger(TemplateWorkspaceImpl.class
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

		String template = getTemplateClient(connection);

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

		// Root required to switch view; must reload values in iclient.
		iclient.setRoot(getRootPath());
		iclient.update();

		// Use template with client
		SwitchClientViewOptions opts = new SwitchClientViewOptions();
		opts.setForce(true);
		connection.switchClientView(template, clientName, opts);
		iclient = connection.getClient(clientName);
		return iclient;
	}

	private String getTemplateClient(IOptionsServer connection) throws Exception {
		// Expand env variables in Template name
		Expand expand = getExpand();
		String template = expand.format(getTemplateName(), false);
		return template;
	}

	public boolean templateExists(IOptionsServer connection) throws Exception {
		String template = getTemplateClient(connection);
		return (connection.getClient(template) != null);
	}

	@Extension
	@Symbol("templateSpec")
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Template (view generated for each node)";
		}

		public FormValidation doCheckTemplateName(@QueryParameter String value) {
			return checkClientName(value);
		}
	}
}
