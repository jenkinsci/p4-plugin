package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMRevision;
import org.jenkinsci.plugins.p4.changes.P4Ref;

import java.util.Arrays;
import java.util.Objects;

public class P4SCMRevision extends SCMRevision {
	private final P4Ref ref;

	public P4SCMRevision(P4SCMHead branch, P4Ref ref) {
		super(branch);
		this.ref = ref;
	}

	public static P4SCMRevision builder(String path, String branch, P4Ref ref) {
		P4Path p4path = new P4Path(path, ref.toString());
		P4SCMHead head = new P4SCMHead(branch, p4path);
		return new P4SCMRevision(head, ref);
	}

	public static P4SCMRevision swarmBuilder(String path, String branch, P4Ref ref) {
		P4SwarmPath p4path = new P4SwarmPath(path, Arrays.asList(path), ref.toString());
		P4SCMHead head = new P4SCMHead(branch, p4path);
		return new P4SCMRevision(head, ref);
	}

	public P4Ref getRef() {
		return ref;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		P4SCMRevision that = (P4SCMRevision) o;
		boolean c = ref.equals(that.ref);
		boolean h = getHead().equals(that.getHead());
		return c && h;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(ref);
	}

	@Override
	public String toString() {
		if (ref == null) {
			return "undefined";
		}
		return "P4SCMRevision: " + getHead() + " REF: " + ref.toString();
	}
}
