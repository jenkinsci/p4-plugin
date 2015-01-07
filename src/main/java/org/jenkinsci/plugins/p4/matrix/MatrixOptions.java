package org.jenkinsci.plugins.p4.matrix;

import hudson.Extension;
import hudson.matrix.MatrixExecutionStrategyDescriptor;
import hudson.matrix.DefaultMatrixExecutionStrategyImpl;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

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
	public static final class DescriptorImpl extends
			MatrixExecutionStrategyDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce: Matrix options";
		}
	}
}
