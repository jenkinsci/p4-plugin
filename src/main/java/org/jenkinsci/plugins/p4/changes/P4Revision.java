package org.jenkinsci.plugins.p4.changes;

import java.io.Serializable;

import org.jenkinsci.plugins.p4.client.ClientHelper;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.exception.P4JavaException;

public class P4Revision implements Serializable {

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

	/**
	 * Look for Change identifier in Client spec.
	 * 
	 * If not found change=0, or if a label then change=-1
	 * 
	 * @param iclient Perforce client
	 */
	public P4Revision(IClient iclient) {
		this.change = 0;

		String desc = iclient.getDescription();
		if (desc != null && !desc.isEmpty()) {
			for (String line : desc.split("\\r?\\n")) {
				if (line.startsWith("Change:")) {
					String args[] = line.split(":", 2);
					try {
						change = Integer.parseInt(args[1].trim());
						this.label = null;
						this.isLabel = false;
					} catch (NumberFormatException e) {
						this.change = -1;
						this.label = args[1];
						this.isLabel = true;
					}
				}
			}
		}
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

	public void save(IClient iclient) throws P4JavaException {
		String desc = iclient.getDescription();
		StringBuffer sb = new StringBuffer();
		boolean saved = false;

		// look for existing line and update
		if (desc != null && !desc.isEmpty()) {
			for (String line : desc.split("\\r?\\n")) {
				if (line.startsWith("Change:")) {
					line = "Change:" + this.toString();
					saved = true;
				}
				sb.append(line + "\n");
			}
		}

		// if no change line than append
		if (!saved) {
			sb.append("Change:" + this.toString() + "\n");
		}

		iclient.setDescription(sb.toString());
		iclient.update();
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
