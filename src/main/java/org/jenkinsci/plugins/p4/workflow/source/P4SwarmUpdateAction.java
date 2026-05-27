package org.jenkinsci.plugins.p4.workflow.source;

import hudson.model.Action;

public class P4SwarmUpdateAction implements Action {
	private String message;

	public P4SwarmUpdateAction(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return null;
	}
}
