package org.jenkinsci.plugins.p4.changes;

public class P4RefBuilder {

	/**
	 * Factory for creating P4ChangeRef and P4LabelRef
	 *
	 * @param revision a String representing a Perforce revision
	 * @return a new P4Ref object.
	 */
	public static P4Ref get(String revision) {
		if (revision.chars().allMatch(Character::isDigit)) {
			long change = Long.parseLong(revision);
			return new P4ChangeRef(change);
		} else {
			return new P4LabelRef(revision);
		}

		// TODO support Graph/Git revisions
	}
}
