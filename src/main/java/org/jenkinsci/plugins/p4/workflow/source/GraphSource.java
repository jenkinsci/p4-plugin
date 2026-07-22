package org.jenkinsci.plugins.p4.workflow.source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

public class GraphSource extends DepotSource {

	private final String graph;

	@DataBoundConstructor
	public GraphSource(String graph) {
		this.graph = graph;
	}

	public String getGraph() {
		return graph;
	}

	@Override
	public Workspace getWorkspace(String charset, String format) {
		return getManualWorkspace(graph, charset, format);
	}

	@Extension
	@Symbol("graphSource")
	public static final class DescriptorImpl extends P4SyncDescriptor {

		public DescriptorImpl() {
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Graph Source(s)";
		}
	}
}
