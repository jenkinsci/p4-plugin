package org.jenkinsci.plugins.p4.workflow;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workflow.source.AbstractSource;
import org.jenkinsci.plugins.p4.workflow.source.DepotSource;
import org.jenkinsci.plugins.p4.workflow.source.StreamSource;
import org.jenkinsci.plugins.p4.workflow.source.TemplateSource;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.io.Serializable;

public class P4Step extends SCMStep implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String credential;

	private String stream = "";
	private String streamAtChange = "";
	private String depotPath = "";
	private String template = "";

	private String charset = "";
	private String format = DescriptorImpl.defaultFormat;

	private Populate populate;
	private Workspace workspace;
	private AbstractSource source;

	@DataBoundConstructor
	public P4Step(String credential) {
		this.credential = credential;
	}

	public String getStream() {
		return stream;
	}

	public String getStreamAtChange() {
		return streamAtChange;
	}

	@DataBoundSetter
	public void setStream(String stream) {
		this.stream = stream;
	}

	@DataBoundSetter
	public void setStreamAtChange(String streamAtChange) {
		this.streamAtChange = streamAtChange;
		if (stream != null && !stream.isEmpty() && !streamAtChange.isEmpty()) {
			StreamSource streamSource = new StreamSource(stream);
			streamSource.setStreamAtChange(streamAtChange);
			source = streamSource;
		}
	}

	public String getDepotPath() {
		return depotPath;
	}

	@DataBoundSetter
	public void setDepotPath(String path) {
		this.depotPath = path;
		if (path != null && !path.isEmpty()) {
			source = new DepotSource(path);
		}
	}

	public String getTemplate() {
		return template;
	}

	@DataBoundSetter
	public void setTemplate(String template) {
		this.template = template;
		if (template != null && !template.isEmpty()) {
			source = new TemplateSource(template);
		}
	}

	public String getCharset() {
		return charset;
	}

	@DataBoundSetter
	public void setWorkspace(Workspace workspace) {
		this.workspace = workspace;
	}

	public Workspace getWorkspace() {
		return workspace;
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

	public AbstractSource getSource() {
		return this.source;
	}

	@DataBoundSetter
	public void setSource(AbstractSource source) {
		this.source = source;
	}

	@NonNull
	@Override
	protected SCM createSCM() {
		P4Browser browser = PerforceScm.findBrowser(credential);

		if (workspace == null) {
			workspace = getSource().getWorkspace(charset, format);
		}

		// use basic populate options if no class provided
		if (populate == null) {
			populate = new AutoCleanImpl();
		}

		PerforceScm scm = new PerforceScm(credential, workspace, null, populate, browser);

		return scm;
	}

	static boolean notNull(String value) {
		return (value != null && !value.isEmpty());
	}

	@Extension(optional = true)
	@Symbol("p4sync")
	public static final class DescriptorImpl extends SCMStepDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}";

		public DescriptorImpl() {
			// Fail now if dependency plugin not loaded. Descriptor.<init> will
			// actually fail anyway, but this is just to be sure.
			PerforceScm.class.hashCode();
		}

		/**
		 * A list of Perforce credential items
		 *
		 * @param project    Jenkins Item
		 * @param credential Perforce Credential
		 * @return A list of Perforce credential items to populate the jelly Select list.
		 */
		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}

		public FormValidation doCheckCredential(@AncestorInPath Item project, @QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(project, value);
		}

		public ListBoxModel doFillCharsetItems() {
			return WorkspaceDescriptor.doFillCharsetItems();
		}

		@Override
		public String getFunctionName() {
			return "p4sync";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "P4 Sync";
		}
	}
}
