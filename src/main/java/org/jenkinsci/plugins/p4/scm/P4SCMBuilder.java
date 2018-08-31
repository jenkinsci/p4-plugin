package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.changes.P4RefBuilder;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;

import java.util.logging.Logger;

public class P4SCMBuilder extends SCMBuilder<P4SCMBuilder, PerforceScm> {

	private static Logger logger = Logger.getLogger(P4SCMBuilder.class.getName());

	private final AbstractP4SCMSource source;
	private final P4SCMHead p4head;
	private final P4Path path;
	private final P4Ref revision;

	public P4SCMBuilder(@NonNull AbstractP4SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision revision) {
		super(PerforceScm.class, head, revision);
		this.source = source;

		if (head instanceof P4SCMHead) {
			this.p4head = (P4SCMHead) head;
			if (p4head.getPath() != null) {
				this.path = p4head.getPath();
			} else {
				this.path = null;
			}
		} else {
			this.path = null;
			this.p4head = null;
		}

		// TODO:
		// Not sure about this, Jenkins seems to ignore revision and send a null.
		// So use P4Path's revision from head...
		if (path != null && path.getRevision() != null) {
			String rev = path.getRevision();
			this.revision = P4RefBuilder.get(rev);
		} else {
			this.revision = null;
		}
	}

	@NonNull
	@Override
	public PerforceScm build() {
		PerforceScm scm = p4head.getScm(source, path, revision);

		if (p4head instanceof P4ChangeRequestSCMHead) {
			P4Review review = new P4Review(p4head.getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
		}

		logger.info("SCM: build: " + path + " head: " + p4head + " rev: " + revision);
		return scm;
	}
}
