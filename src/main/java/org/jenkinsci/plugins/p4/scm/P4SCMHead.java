package org.jenkinsci.plugins.p4.scm;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4Ref;

import java.util.HashMap;
import java.util.Map;

public class P4SCMHead extends SCMHead {

	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void addAliases() {
		Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.p4.scm.P4Head", P4SCMHead.class);
	}

	private final P4Path path;
	private Map<String, String> swarmParams;

	public P4SCMHead(String name, P4Path path) {
		super(name);
		this.path = path;
		swarmParams = new HashMap<>();
	}

	public P4Path getPath() {
		return path;
	}

	@Override
	public String toString() {
		return "P4SCMHead: " + getName() + " (" + path + ")";
	}

	public Map<String, String> getSwarmParams() {
		return swarmParams;
	}

	public void setSwarmParams(Map<String, String> swarmParams) {
		this.swarmParams = swarmParams;
	}


	public PerforceScm getScm(AbstractP4ScmSource source, P4Path path, P4Ref revision) {
		return new PerforceScm(source, path, revision);
	}
}