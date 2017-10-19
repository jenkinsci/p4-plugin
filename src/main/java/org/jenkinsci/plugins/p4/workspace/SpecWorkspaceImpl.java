package org.jenkinsci.plugins.p4.workspace;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.jenkinsci.plugins.p4.client.NavigateHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;

public class SpecWorkspaceImpl extends Workspace implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String name;
	private final String specPath;

	private static Logger logger = Logger.getLogger(SpecWorkspaceImpl.class.getName());

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
	public SpecWorkspaceImpl(String charset, boolean pinHost, String name, String specPath) {
		super(charset, pinHost);
		this.name = name;
		this.specPath = specPath;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user) throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();

		// fetch spec
		String specPathFull = getExpand().format(getSpecPath(), false);
		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(specPathFull);
		GetFileContentsOptions printOpts = new GetFileContentsOptions();
		printOpts.setNoHeaderLine(true);
		InputStream ins = connection.getFileContents(file, printOpts);

		// parse spec
		String spec = IOUtils.toString(ins, "UTF-8");
		spec = getExpand().format(spec, false);
		connection.execInputStringMapCmd("client", new String[]{"-i"}, spec);

		// get client
		IClient iclient = connection.getClient(clientName);
		iclient.setName(clientName);
		iclient.setOwnerName(user);

		// save and return IClient
		iclient.refresh();
		return iclient;
	}

	@Extension
	@Symbol("specFileSpec")
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		@Override
		public String getDisplayName() {
			return "Spec File (load workspace spec from file in Perforce)";
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

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 *
		 * @param value The text that the user entered.
		 * @return suggestion
		 */
		public AutoCompletionCandidates doAutoCompleteSpecPath(@QueryParameter String value) {
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