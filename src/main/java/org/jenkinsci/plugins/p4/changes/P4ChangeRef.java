package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

import java.io.Serial;
import java.util.List;

public class P4ChangeRef implements P4Ref {

	@Serial
	private static final long serialVersionUID = 1L;

	private final long change;

	public P4ChangeRef(long change) {
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
	public long getChange() {
		return change;
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
		if (obj instanceof P4ChangeRef ref) {
			return ref.toString().equals(toString());
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
		if (equals(obj)) {
			return 0;
		}
		if (obj instanceof P4ChangeRef ref) {
			return (int)(change - ref.getChange());
		}
		throw new ClassCastException();
	}
}
