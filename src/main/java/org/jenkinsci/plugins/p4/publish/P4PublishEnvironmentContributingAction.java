package org.jenkinsci.plugins.p4.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

public class P4PublishEnvironmentContributingAction extends InvisibleAction implements EnvironmentContributingAction {
	private final String publishedChangeId;

	public P4PublishEnvironmentContributingAction(String publishedChangeID) {
		this.publishedChangeId = publishedChangeID;
	}

	@Override
	public void buildEnvironment(@NonNull Run<?, ?> run, @NonNull EnvVars env) {
		env.put("P4_PUBLISH_CHANGELIST", publishedChangeId);
	}
}
