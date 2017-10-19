package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import java.util.List;

public class P4GraphRequestSCMHead extends P4Head implements ChangeRequestSCMHead {

	private static final long serialVersionUID = 1L;

	private final SCMHead target;
	private final String repo;
	private final String branch;

	P4GraphRequestSCMHead(String name, String repo, String branch, List<P4Path> paths, SCMHead target) {
		super(name, paths);
		this.target = target;
		this.repo = repo;
		this.branch = branch;
	}

	@Override
	public String getId() {
		return getName();
	}

	public String getRepo() {
		return repo;
	}

	public String getBranch() {
		return branch;
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