package org.jenkinsci.plugins.p4.build;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;

public class P4StreamEnvironmentContributionAction implements EnvironmentContributingAction {
	private final String streamAtChange;

	public P4StreamEnvironmentContributionAction(String streamAtChange) {
		this.streamAtChange = streamAtChange;
	}

	@Override
	public String getIconFileName() {
		return "";
	}

	@Override
	public String getDisplayName() {
		return "";
	}

	@Override
	public String getUrlName() {
		return "";
	}

	@Override
	public void buildEnvironment(@NonNull Run<?, ?> run, @NonNull EnvVars env) {
		env.put("P4_STREAM_AT_CHANGE", streamAtChange);
	}
}
