package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;

public class StaticWorkspaceImpl extends Workspace {

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
	public StaticWorkspaceImpl(String charset, String name) {
		super(charset);
		this.name = name;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {
		IClient iclient = connection.getClient(getName());
		if (iclient == null) {
			throw new Exception("Undefined workspace: " + getName());
		}
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
}
