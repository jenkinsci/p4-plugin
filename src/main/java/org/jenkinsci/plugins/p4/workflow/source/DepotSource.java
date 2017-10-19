package org.jenkinsci.plugins.p4.workflow.source;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.kohsuke.stapler.DataBoundConstructor;

public class DepotSource extends AbstractSource {

	private String depot;

	public DepotSource() {

	}

	@DataBoundConstructor
	public DepotSource(String depot) {
		this.depot = depot;
	}

	public String getDepot() {
		return depot;
	}

	public Workspace getWorkspace(String charset, String format) {
		return getManualWorkspace(depot, charset, format);
	}

	protected Workspace getManualWorkspace(String source, String charset, String format) {
		Workspace workspace = null;
		String view = AbstractSource.getClientView(source, format);
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, null, "local", view, null, null, null, true);
		workspace = new ManualWorkspaceImpl(charset, false, format, spec);
		return workspace;
	}

	@Extension
	@Symbol("depotSource")
	public static final class DescriptorImpl extends P4SyncDescriptor {

		public DescriptorImpl() {
		}

		@Override
		public String getDisplayName() {
			return "Depot Source(s)";
		}
	}
}
