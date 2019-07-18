package org.jenkinsci.plugins.p4.scm;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import jenkins.scm.api.SCMRevision;
import org.jenkinsci.plugins.p4.changes.P4Ref;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class P4SCMRevision extends SCMRevision {

	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void addAliases() {
		Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.p4.scm.P4Revision", P4SCMRevision.class);
	}

	private final P4Ref ref;

	public P4SCMRevision(P4SCMHead branch, P4Ref ref) {
		super(branch);
		this.ref = ref;
	}

	public static P4SCMRevision builder(String path, String branch, P4Ref ref) {
		P4Path p4path = new P4Path(path);
		p4path.setRevision(ref.toString());
		P4SCMHead head = new P4SCMHead(branch, p4path);
		return new P4SCMRevision(head, ref);
	}

	public static P4SCMRevision swarmBuilder(String path, String branch, P4Ref ref) {
		List<String> mappings = new ArrayList<>();
		mappings.add(path + "/...");

		P4Path p4Path = new P4Path(path);
		p4Path.setRevision(ref.toString());
		p4Path.setMappings(mappings);
		P4SCMHead head = new P4SCMHead(branch, p4Path);
		return new P4SCMRevision(head, ref);
	}

	public static P4SCMRevision swarmBuilder(String path, String branch, P4Ref ref, String reviewID) {
		List<String> mappings = new ArrayList<>();
		mappings.add(path + "/...");

		P4Path p4Path = new P4Path(path);
		p4Path.setRevision(reviewID);
		p4Path.setMappings(mappings);
		String trgName = reviewID;
		P4SCMHead target = new P4SCMHead(trgName, p4Path);
		P4ChangeRequestSCMHead head = new P4ChangeRequestSCMHead(trgName, reviewID, p4Path, target);
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
		return ref.toString();
	}
}
