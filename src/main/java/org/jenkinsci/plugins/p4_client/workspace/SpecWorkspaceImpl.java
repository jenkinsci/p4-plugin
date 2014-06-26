package org.jenkinsci.plugins.p4_client.workspace;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.p4_client.client.ConnectionFactory;
import org.jenkinsci.plugins.p4_client.client.NavigateHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.server.IOptionsServer;

public class SpecWorkspaceImpl extends Workspace {

	private final String name;
	private final String specPath;

	private static Logger logger = Logger.getLogger(SpecWorkspaceImpl.class
			.getName());

	@Override
	public String getName() {
		return name;
	}

	public String getSpecPath() {
		return specPath;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.SPEC;
	}

	@DataBoundConstructor
	public SpecWorkspaceImpl(String charset, String name, String specPath) {
		super(charset);
		this.name = name;
		this.specPath = specPath;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {

		IClient iclient = connection.getClient(getName());
		if (iclient == null) {
			logger.info("Creating stream client: " + getName());
			Client implClient = new Client();
			implClient.setName(getName());
			connection.createClient(implClient);
			iclient = connection.getClient(getName());
		}

		// Set owner (not set during create)
		iclient.setOwnerName(user);
		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(specPath);
		GetFileContentsOptions printOpts = new GetFileContentsOptions();
		printOpts.setNoHeaderLine(true);
		InputStream ins = connection.getFileContents(file, printOpts);

		String spec = IOUtils.toString(ins, "UTF-8");
		connection.execInputStringMapCmd("client", new String[] { "-i" }, spec);
		iclient.refresh();
		
		return iclient;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Spec File (load workspace spec from file in Perforce)";
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

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 * 
		 * @param value
		 *            The text that the user entered.
		 */
		public AutoCompletionCandidates doAutoCompleteSpecPath(
				@QueryParameter String value) {
			return NavigateHelper.getPath(value);
		}

		public FormValidation doCheckSpecPath(@QueryParameter String value) {
			try {
				IOptionsServer p4 = ConnectionFactory.getConnection();
				List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(value);
				GetFileContentsOptions printOpts = new GetFileContentsOptions();
				InputStream ins = p4.getFileContents(file, printOpts);

				if (ins != null) {
					return FormValidation.ok();
				}
				return FormValidation.error("Unknown file: " + value);
			} catch (Exception e) {
				return FormValidation.error(e.getMessage());
			}
		}
	}
}