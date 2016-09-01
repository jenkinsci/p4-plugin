package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;

public class StaticWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String name;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.STATIC;
	}

	@DataBoundConstructor
	public StaticWorkspaceImpl(String charset, boolean pinHost, String name) {
		super(charset, pinHost);
		this.name = name;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();
		IClient iclient = connection.getClient(clientName);
		return iclient;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Static (static view, master only)";
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 *
		 * @param value The text that the user entered.
		 * @return suggestion
		 */
		public AutoCompletionCandidates doAutoCompleteName(
				@QueryParameter String value) {
			return autoCompleteName(value);
		}

		public FormValidation doCheckName(@QueryParameter String value) {
			return checkClientName(value);
		}
	}
}
