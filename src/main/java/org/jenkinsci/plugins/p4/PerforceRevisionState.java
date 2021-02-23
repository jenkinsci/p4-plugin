package org.jenkinsci.plugins.p4;

import hudson.scm.SCMRevisionState;
import org.jenkinsci.plugins.p4.changes.P4Ref;

public class PerforceRevisionState extends SCMRevisionState {

	private P4Ref change;

	PerforceRevisionState(P4Ref change) {
		this.change = change;
	}

	public P4Ref getChange() {
		return change;
	}

	public void setChange(P4Ref change) {
		this.change = change;
	}
}
