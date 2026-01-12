package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.IFileSpec;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

import java.util.List;

public class P4PollRef implements P4Ref {

	private final long change;
	private final String pollPath;

	public P4PollRef(long change, String pollPath) {
		this.change = change;
		this.pollPath = pollPath;
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
	public long getChange() {
		return change;
	}

	public String getPollPath() {
		return pollPath;
	}

	@Override
	public List<IFileSpec> getFiles(ConnectionHelper p4, int limit) throws Exception {
		return p4.getChangeFiles(change, limit);
	}

	@Override
	public String toString() {
		return Long.toString(change);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4PollRef) {
			P4PollRef ref = (P4PollRef) obj;
			if (ref.getPollPath().equals(this.pollPath) && ref.getChange()==this.change) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int compareTo(Object obj) {
		if (equals(obj)) {
			return 0;
		}
		if (obj instanceof P4PollRef) {
			P4PollRef ref = (P4PollRef) obj;
			return (int) (change - ref.getChange());
		}
		throw new ClassCastException();
	}
}
