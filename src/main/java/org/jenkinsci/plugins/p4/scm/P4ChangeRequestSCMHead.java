package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import java.util.List;

public class P4ChangeRequestSCMHead extends P4Head implements ChangeRequestSCMHead {

	private static final long serialVersionUID = 1L;

	private final SCMHead target;
	private final String review;

	P4ChangeRequestSCMHead(String name, String review, List<P4Path> paths, SCMHead target) {
		super(name, paths);
		this.target = target;
		this.review = review;
	}

	@Override
	public String getId() {
		return getName();
	}

	public String getReview() {
		return review;
	}

	/**
	 * Branch to which this change would be merged or applied if it were accepted.
	 *
	 * @return a “target” or “base” branch
	 */
	@Override
	public SCMHead getTarget() {
		return target;
	}
}
