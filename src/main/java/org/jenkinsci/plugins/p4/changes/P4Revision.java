package org.jenkinsci.plugins.p4.changes;

import org.jenkinsci.plugins.p4.client.ClientHelper;

public class P4Revision {

	private final int change;
	private final String label;
	private final boolean isLabel;

	public P4Revision(String label) {
		this.change = -1;
		this.label = label;
		this.isLabel = true;
	}

	public P4Revision(int change) {
		this.change = change;
		this.label = null;
		this.isLabel = false;
	}

	public boolean isLabel() {
		return isLabel;
	}

	public String toString() {
		if (isLabel) {
			return label;
		} else {
			return Integer.toString(change);
		}
	}
	
	public int getChange() {
		return change;
	}

	public P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		if (isLabel) {
			cl.setLabel(p4, label);
		} else {
			cl.setChange(p4, change);
		}
		return cl;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4Revision) {
			P4Revision rev = (P4Revision) obj;
			if (rev.toString().equals(toString())) {
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
}
