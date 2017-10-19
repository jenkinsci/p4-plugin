package org.jenkinsci.plugins.p4.changes;

import org.jenkinsci.plugins.p4.client.ClientHelper;

public class P4LabelRef implements P4Ref {

	private static final long serialVersionUID = 1L;

	private final String label;

	public P4LabelRef(String label) {
		this.label = label;
	}

	@Override
	public P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		cl.setLabel(p4, label);
		return cl;
	}

	@Override
	public boolean isLabel() {
		return true;
	}

	@Override
	public boolean isCommit() {
		return false;
	}

	@Override
	public long getChange() {
		return -1L;
	}

	@Override
	public String toString() {
		return label;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4LabelRef) {
			P4LabelRef ref = (P4LabelRef) obj;
			if (ref.toString().equals(toString())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 89 * hash + (toString().hashCode());
		return hash;
	}

	@Override
	public int compareTo(Object obj) {
		if (equals(obj)) {
			return 0;
		}
		if (obj instanceof P4LabelRef) {
			P4LabelRef ref = (P4LabelRef) obj;

			if (ref.toString().equals("now"))
				return -1;
			if (label.equals("now"))
				return 1;
			if (ref.isLabel()) {
				return 0;
			}
		}
		throw new ClassCastException();
	}
}
