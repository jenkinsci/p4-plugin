package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.util.FormValidation;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.SwitchClientViewOptions;
import com.perforce.p4java.server.IOptionsServer;

public class TemplateWorkspaceImpl extends Workspace {

	private final String templateName;
	private final String format;

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
	public WorkspaceType getType() {
		return WorkspaceType.TEMPLATED;
	}

	@DataBoundConstructor
	public TemplateWorkspaceImpl(String charset, boolean pinHost,
			String templateName, String format) {
		super(charset, pinHost);
		this.templateName = templateName;
		this.format = format;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {

		// Expand env variables in Template name
		Expand expand = getExpand();
		String template = expand.format(getTemplateName(), false);

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
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
		}
		// Set owner (not set during create)
		iclient.setOwnerName(user);

		// set line endings explicitly (JENKINS-28760)
		iclient.setLineEnd(itemplate.getLineEnd());

		// set options explicitly (JENKINS-30546)
		iclient.setOptions(itemplate.getOptions());

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

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

		@Override
		public String getDisplayName() {
			return "Template (view generated for each node)";
		}

		public FormValidation doCheckTemplateName(@QueryParameter String value) {
			return checkClientName(value);
		}

	}
}
