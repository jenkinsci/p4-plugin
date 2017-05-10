package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import org.jenkinsci.plugins.p4.client.ClientHelper;

public class P4ChangeRef implements P4Ref {

	private static final long serialVersionUID = 1L;

	private final int change;

	public P4ChangeRef(int change) {
		this.change = change;
	}

	@Override
	public P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		IChangelistSummary summary = p4.getChangeSummary(change);
		cl.setChange(p4, summary);
		return cl;
	}

	@Override
	public boolean isLabel() {
		return false;
	}

	@Override
	public boolean isCommit() {
		return false;
	}

	@Override
	public int getChange() {
		return change;
	}

	@Override
	public String toString() {
		return Integer.toString(change);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4ChangeRef) {
			P4ChangeRef ref = (P4ChangeRef) obj;
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
		if (obj instanceof P4ChangeRef) {
			P4ChangeRef ref = (P4ChangeRef) obj;
			return change - ref.getChange();
		}
		throw new ClassCastException();
	}
}
