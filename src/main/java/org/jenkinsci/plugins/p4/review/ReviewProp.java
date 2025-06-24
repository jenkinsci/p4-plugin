package org.jenkinsci.plugins.p4.review;

public enum ReviewProp {

	// Basic Swarm Review properties
	SWARM_STATUS("status", true),           // "committed" or "shelved" see tasks.CheckoutStatus
	SWARM_REVIEW("review", true),
	SWARM_PASS("pass", true),
	SWARM_FAIL("fail", true),
	SWARM_UPDATE("update", true),

	// Extended Swarm Review properties
	SWARM_PROJECT("swarm_project", true),
	SWARM_BRANCH("swarm_branch", true),
	SWARM_PATH("swarm_path", true),

	// Core Perforce properties
	P4_CHANGE("change", true),
	P4_LABEL("label", false),               // reserved by Jenkins for Agent Axes
	P4_PORT("p4port", true),                // must match P4PORT string in credential

	// SCM MultiBranch properties
	EVENT_TYPE("event_type", true);

	public static final String NAMESPACE = "p4.";
	final private String prop;
	final private boolean load;


	ReviewProp(String prop, boolean load) {
		this.prop = prop;
		this.load = load;
	}

	public String toString() {
		return NAMESPACE + prop;
	}

	public String getProp() {
		return prop;
	}

	public boolean isLoad() {
		return load;
	}

	public static boolean isProp(String prop) {
		for (ReviewProp p : ReviewProp.values()) {
			if (p.isLoad() && p.getProp().equals(prop))
				return true;
		}
		return false;
	}
}
