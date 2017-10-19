package org.jenkinsci.plugins.p4.workflow.source;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

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

		@Override
		public String getDisplayName() {
			return "Template Workspace";
		}
	}
}
