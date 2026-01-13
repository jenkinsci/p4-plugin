package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.Serial;
import java.io.Serializable;

public class P4Revision implements Serializable, Comparable {

	@Serial
	private static final long serialVersionUID = 1L;

	private int change;
	private String label;
	private boolean isLabel;

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
			IChangelistSummary summary = p4.getChangeSummary(change);
			cl.setChange(p4, summary);
		}
		return cl;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4Revision rev) {
			return rev.toString().equals(toString());
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
	public int compareTo(@NonNull Object obj) {
		if(equals(obj)) {
			return 0;
		}
		if (obj instanceof P4Revision rev) {

			if(rev.isLabel && rev.toString().equals("now"))
				return -1;
			if(isLabel && label.equals("now"))
				return 1;
			if(isLabel || rev.isLabel) {
				return 0;
			} else {
				return change - rev.getChange();
			}
		}
		throw new ClassCastException();
	}
}
