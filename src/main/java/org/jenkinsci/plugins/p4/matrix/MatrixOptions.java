package org.jenkinsci.plugins.p4.matrix;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixExecutionStrategyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Logger;

public class MatrixOptions extends DefaultMatrixExecutionStrategyImpl {

	protected static final Logger LOGGER = Logger.getLogger(MatrixOptions.class
			.getName());

	private final boolean buildParent;

	public boolean isBuildParent() {
		return buildParent;
	}

	@DataBoundConstructor
	public MatrixOptions(boolean buildParent, boolean buildAxes,
			boolean runSequentially) {
		super(runSequentially, false, null, null, null);
		this.buildParent = buildParent;
	}

	@Extension
	@Symbol("matrix")
	public static final class DescriptorImpl extends
			MatrixExecutionStrategyDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce: Matrix options";
		}
	}
}
