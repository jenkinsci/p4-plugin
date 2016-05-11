package org.jenkinsci.plugins.p4.workflow;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4WebBrowser;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class P4Step extends SCMStep {

	private static final long serialVersionUID = 1L;

	private final String credential;

	private String stream = "";
	private String depotPath = "";
	private String template = "";

	private String charset = "";
	private String format = DescriptorImpl.defaultFormat;
	
	private Populate populate;

	@DataBoundConstructor
	public P4Step(String credential) {
		this.credential = credential;
	}

	public String getStream() {
		return stream;
	}

	@DataBoundSetter
	public void setStream(String stream) {
		this.stream = stream;
	}

	public String getDepotPath() {
		return depotPath;
	}

	@DataBoundSetter
	public void setDepotPath(String path) {
		this.depotPath = path;
	}

	public String getTemplate() {
		return template;
	}

	@DataBoundSetter
	public void setTemplate(String template) {
		this.template = template;
	}

	public String getCharset() {
		return charset;
	}

	@DataBoundSetter
	public void setCharset(String charset) {
		this.charset = charset;
	}

	public String getFormat() {
		return format;
	}

	@DataBoundSetter
	public void setFormat(String format) {
		this.format = format;
	}

	public String getCredential() {
		return credential;
	}

	@DataBoundSetter
	public void setPopulate(Populate populate) {
		this.populate = populate;
	}
	
	public Populate getPopulate() {
		return populate;
	}



	@Override
	protected SCM createSCM() {
		P4WebBrowser browser = null;

		Workspace workspace = null;
		if (notNull(stream))
			workspace = new StreamWorkspaceImpl(charset, false, stream, format);
		else if (notNull(template))
			workspace = new TemplateWorkspaceImpl(charset, false, template, format);
		else if (notNull(depotPath)) {
			String view = depotPath + "/..." + " " + "//" + format + "/...";
			WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, null, "local", view);

			workspace = new ManualWorkspaceImpl(charset, false, format, spec);
		}

		// use basic populate options if no class provided
		if(populate == null) {
			populate = new AutoCleanImpl(true, true, false, false, null, null);
		}

		PerforceScm scm = new PerforceScm(credential, workspace, null, populate, browser);

		return scm;
	}

	static boolean notNull(String value) {
		return (value != null && !value.isEmpty());
	}

	@Extension(optional = true)
	public static final class DescriptorImpl extends SCMStepDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

		public DescriptorImpl() {
			// Fail now if dependency plugin not loaded. Descriptor.<init> will
			// actually fail anyway, but this is just to be sure.
			PerforceScm.class.hashCode();
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 * 
		 * @return A list of Perforce credential items to populate the jelly
		 *         Select list.
		 */
		public ListBoxModel doFillCredentialItems() {
			return P4CredentialsImpl.doFillCredentialItems();
		}

		public FormValidation doCheckCredential(@QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(value);
		}

		public AutoCompletionCandidates doAutoCompleteStream(@QueryParameter String value) {
			return WorkspaceDescriptor.doAutoCompleteStreamName(value);
		}

		public AutoCompletionCandidates doAutoCompleteTemplate(@QueryParameter String value) {
			return WorkspaceDescriptor.doAutoCompleteTemplateName(value);
		}

		// check there is only one source
		private static boolean hasMultiple(String stream, String template, String path) {
			if (P4Step.notNull(stream)) {
				if (P4Step.notNull(template) || P4Step.notNull(path)) {
					return true;
				}
			}
			if (P4Step.notNull(template)) {
				if (P4Step.notNull(stream) || P4Step.notNull(path)) {
					return true;
				}
			}
			if (P4Step.notNull(path)) {
				if (P4Step.notNull(template) || P4Step.notNull(stream)) {
					return true;
				}
			}

			return false;
		}

		public FormValidation doCheckStream(@QueryParameter String value, @QueryParameter String template,
				@QueryParameter String path) {
			if (P4Step.notNull(value)) {
				if (hasMultiple(value, template, path)) {
					return FormValidation.error("Specify only one source.");
				}
				return WorkspaceDescriptor.doCheckStreamName(value);
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckTemplate(@QueryParameter String value, @QueryParameter String stream,
				@QueryParameter String path) {
			if (P4Step.notNull(value)) {
				if (hasMultiple(stream, value, path)) {
					return FormValidation.error("Specify only one source.");
				}
				return WorkspaceDescriptor.checkClientName(value);
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckPath(@QueryParameter String value, @QueryParameter String stream,
				@QueryParameter String template) {
			if (P4Step.notNull(value)) {
				if (hasMultiple(stream, template, value)) {
					return FormValidation.error("Specify only one source.");
				}
				return FormValidation.ok();
			}
			return FormValidation.ok();
		}

		public ListBoxModel doFillCharsetItems() {
			return WorkspaceDescriptor.doFillCharsetItems();
		}

		@Override
		public String getFunctionName() {
			return "p4sync";
		}

		@Override
		public String getDisplayName() {
			return "P4 Sync";
		}
	}
}
