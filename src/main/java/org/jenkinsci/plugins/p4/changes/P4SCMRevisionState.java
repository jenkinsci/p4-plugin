package org.jenkinsci.plugins.p4.changes;

import hudson.scm.SCMRevisionState;

public class P4SCMRevisionState extends SCMRevisionState {

	public static final String URLNAME = "changestate";

	private final P4Ref changeState;

	public P4SCMRevisionState(P4Ref changeState) {
		this.changeState = changeState;
	}

	@Override
	public String getIconFileName() {
		return "/plugin/p4/icons/changelist.gif";
	}

	@Override
	public String getDisplayName() {
		return "P4 Change State";
	}

	@Override
	public String getUrlName() {
		return URLNAME;
	}

	public P4Ref getChangeState() {
		return changeState;
	}

}
