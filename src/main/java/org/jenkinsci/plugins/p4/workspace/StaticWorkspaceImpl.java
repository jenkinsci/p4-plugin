package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.io.Serializable;

public class StaticWorkspaceImpl extends Workspace implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String name;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.STATIC;
	}

	@Deprecated
	public StaticWorkspaceImpl(String charset, boolean pinHost, String name, boolean clone) {
		super(charset, pinHost, false);
		this.name = name;
	}

	@DataBoundConstructor
	public StaticWorkspaceImpl(String charset, boolean pinHost, String name) {
		super(charset, pinHost, false);
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
	@Symbol("staticSpec")
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@NonNull
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
