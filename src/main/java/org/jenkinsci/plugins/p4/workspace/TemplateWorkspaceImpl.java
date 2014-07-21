package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.GetClientsOptions;
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
	public TemplateWorkspaceImpl(String charset, String templateName,
			String format) {
		super(charset);
		this.templateName = templateName;
		this.format = format;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();
				
		String template = getTemplateName();
		IClient iclient = connection.getClient(clientName);
		if (iclient == null) {
			logger.info("Creating template client: " + clientName);
			Client implClient = new Client();
			implClient.setName(clientName);
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
		}
		// Set owner (not set during create)
		iclient.setOwnerName(user);

		// Root required to switch view; must reload values in iclient.
		iclient.setRoot(getRootPath());
		SwitchClientViewOptions opts = new SwitchClientViewOptions();
		opts.setForce(true);
		connection.switchClientView(template, clientName, opts);
		iclient = connection.getClient(clientName);
		return iclient;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		public static final String defaultFormat = "jenkins-${node}-${project}";

		@Override
		public String getDisplayName() {
			return "Template (view generated for each node)";
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 * 
		 * @param value
		 *            The text that the user entered.
		 */
		public AutoCompletionCandidates doAutoCompleteTemplateName(
				@QueryParameter String value) {

			AutoCompletionCandidates c = new AutoCompletionCandidates();
			try {
				IOptionsServer iserver = ConnectionFactory.getConnection();
				if (iserver != null && value.length() > 0) {
					List<IClientSummary> list;
					GetClientsOptions opts = new GetClientsOptions();
					opts.setMaxResults(10);
					opts.setNameFilter(value + "*");
					list = iserver.getClients(opts);
					for (IClientSummary l : list) {
						c.add(l.getName());
					}
				}
			} catch (Exception e) {
			}

			return c;
		}

		public FormValidation doCheckTemplateName(@QueryParameter String value) {
			return checkClientName(value);
		}

		public FormValidation doCheckFormat(@QueryParameter final String value) {
			if (value == null || value.isEmpty())
				return FormValidation
						.error("Workspace Name format is mandatory.");

			if (value.contains("${") && value.contains("}")) {
				return FormValidation.ok();
			}
			return FormValidation.error("Workspace Name format error.");
		}
	}
}
