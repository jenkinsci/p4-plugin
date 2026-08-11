package org.jenkinsci.plugins.p4.workflow.source;

import hudson.model.InvisibleAction;

public class P4SwarmUpdateAction extends InvisibleAction {
	private String message;

	public P4SwarmUpdateAction(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
