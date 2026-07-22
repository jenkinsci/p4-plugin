package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.graph.ICommit;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

import java.io.Serial;
import java.util.List;

public class P4GraphRef implements P4Ref {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String repo;
	private final String sha;
	private final long date;

	private transient final ICommit commit;

	public P4GraphRef(ConnectionHelper p4, String id) throws P4JavaException {

		if (id != null && !id.isEmpty() && id.contains("@")) {
			String[] parts = id.split("@");
			if (parts.length == 2) {
				this.repo = parts[0];
				String sha = parts[1];
				this.commit = p4.getGraphCommit(sha, repo);
				this.date = commit.getCommitterDate().getTime();
				this.sha = sha;
				return;
			}
		}

		this.repo = null;
		this.commit = null;
		this.date = 0L;
		this.sha = null;
	}

	public P4GraphRef(String repo, ICommit commit) {
		this.repo = repo;
		this.commit = commit;
		this.date = commit.getCommitterDate().getTime();
		this.sha = commit.getCommit();
	}

	@Override
	public P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		cl.setGraphCommit(p4, repo, sha);
		return cl;
	}

	@Override
	public boolean isLabel() {
		return false;
	}

	@Override
	public boolean isCommit() {
		return true;
	}

	@Override
	public long getChange() {
		return -1L;
	}

	@Override
	public List<IFileSpec> getFiles(ConnectionHelper p4, int limit) throws Exception {
		List<IFileSpec> list = p4.getCommitFiles(repo, sha);
		return list.subList(0, limit);
	}

	public long getDate() {
		return date;
	}

	public String getRepo() {
		return repo;
	}

	public String getSha() {
		return sha;
	}

	@Override
	public String toString() {
		return repo + "@" + sha;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof P4GraphRef ref) {
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
		if (obj instanceof P4GraphRef ref) {
			long diff = getDate() - ref.getDate();
			return (int) diff;
		}
		throw new ClassCastException();
	}
}

