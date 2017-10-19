package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMRevision;
import org.jenkinsci.plugins.p4.changes.P4Ref;

import java.util.Objects;

public class P4Revision extends SCMRevision {
	private final P4Ref ref;

	P4Revision(P4Head branch, P4Ref ref) {
		super(branch);
		this.ref = ref;
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
		P4Revision that = (P4Revision) o;
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
		return ref.toString();
	}
}
