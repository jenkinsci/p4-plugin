package org.jenkinsci.plugins.p4.review;

public enum ApproveState {

	REVIEW("needsReview", "Needs Review", false),
	REVISION("needsRevision", "Needs Revision", false),
	APPROVED("approved", "Approve", false),
	COMMIT("approved:commit", "Approve and Commit", true),
	REJECTED("rejected", "Reject", false),
	ARCHIVED("Archived", "Archive", false),
	VOTE_UP("up", "Vote Up", false),
	VOTE_DOWN("down", "Vote Down", false);

	private final String id;
	private final String description;
	private final boolean commit;

	ApproveState(String id, String description, boolean commit) {
		this.id = id;
		this.description = description;
		this.commit = commit;
	}

	public String getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public boolean isCommit() {
		return commit;
	}

	public static ApproveState parse(String value) {
		for (ApproveState s : ApproveState.values()) {
			if (s.name().equalsIgnoreCase(value)) {
				return s;
			}
		}
		return null;
	}
}
