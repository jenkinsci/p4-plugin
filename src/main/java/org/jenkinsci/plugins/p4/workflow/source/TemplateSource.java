package org.jenkinsci.plugins.p4.workflow.source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class TemplateSource extends AbstractSource {

	private String template;

	@DataBoundConstructor
	public TemplateSource(String template) {
		this.template = template;
	}

	@Override
	public Workspace getWorkspace(String charset, String format) {
		return  new TemplateWorkspaceImpl(charset, false, this.template, format);
	}

	public String getTemplate() {
		return template;
	}

	@Extension
	@Symbol("templateSource")
	public static final class DescriptorImpl extends P4SyncDescriptor {

		public DescriptorImpl() {
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Template Workspace";
		}

		public AutoCompletionCandidates doAutoCompleteTemplate(@QueryParameter String value) {
			return WorkspaceDescriptor.doAutoCompleteTemplateName(value);
		}

		public FormValidation doCheckTemplate(@QueryParameter String value) {
			return WorkspaceDescriptor.checkClientName(value);
		}
	}
}
