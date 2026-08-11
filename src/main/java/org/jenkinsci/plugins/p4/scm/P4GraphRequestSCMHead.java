package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import java.io.Serial;

public class P4GraphRequestSCMHead extends P4SCMHead implements ChangeRequestSCMHead {

	@Serial
	private static final long serialVersionUID = 1L;

	private final SCMHead target;
	private final String repo;
	private final String branch;

	P4GraphRequestSCMHead(String name, String repo, String branch, P4Path path, SCMHead target) {
		super(name, path);
		this.target = target;
		this.repo = repo;
		this.branch = branch;
	}

	@NonNull
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
	@NonNull
	@Override
	public SCMHead getTarget() {
		return target;
	}
}