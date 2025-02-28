package org.jenkinsci.plugins.p4.populate;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;

public class GraphHybridImpl extends Populate {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Graph Depot sync of workspace (optional have update)
	 *
	 * @param quiet    Perforce quiet option
	 * @param pin      Change or label to pin the sync
	 * @param parallel Parallel sync options
	 */
	@DataBoundConstructor
	public GraphHybridImpl(boolean quiet, String pin, ParallelSync parallel) {
		super(true, true, false, quiet, pin, parallel);
	}

	@Extension
	@Symbol("graphClean")
	public static final class DescriptorImpl extends PopulateDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Graph force clean and sync";
		}

		@Override
		public boolean isGraphCompatible() {
			return true;
		}
	}
}