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

public class P4ScmBuilder extends SCMBuilder<P4ScmBuilder, PerforceScm> {

	private static Logger logger = Logger.getLogger(P4ScmBuilder.class.getName());

	private final AbstractP4ScmSource source;
	private final P4Path path;
	private final P4Ref revision;

	public P4ScmBuilder(@NonNull AbstractP4ScmSource source, @NonNull SCMHead head, @CheckForNull SCMRevision revision) {
		super(PerforceScm.class, head, revision);
		this.source = source;

		if (head instanceof P4Head) {
			P4Head p4head = (P4Head) head;
			if (p4head.getPath() != null) {
				this.path = p4head.getPath();
			} else {
				this.path = null;
			}
		} else {
			this.path = null;
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

		logger.info("SCM: P4ScmBuilder: " + head + "(" + revision + ")");
	}

	@NonNull
	@Override
	public PerforceScm build() {
		if (head() instanceof P4ChangeRequestSCMHead) {
			PerforceScm scm = new PerforceScm(source, path, revision);

			P4Review review = new P4Review(head().getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
			return scm;
		}
		if (head() instanceof P4GraphRequestSCMHead) {
			PerforceScm scm = new PerforceScm(source, path, revision);
			return scm;
		}
		if (head() instanceof P4Head) {
			PerforceScm scm = new PerforceScm(source, path, revision);
			return scm;
		}
		throw new IllegalArgumentException("SCMHead not a Perforce instance!");
	}
}
