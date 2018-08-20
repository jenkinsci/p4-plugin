package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;

public class P4ScmBuilder extends SCMBuilder<P4ScmBuilder, PerforceScm> {

	private final AbstractP4ScmSource source;
	private final P4Revision revision;

	public P4ScmBuilder(@NonNull AbstractP4ScmSource source, @NonNull SCMHead head, @CheckForNull SCMRevision revision) {
		super(PerforceScm.class, head, revision);
		this.source = source;

		if(revision instanceof P4Revision) {
			this.revision = (P4Revision) revision;
		} else {
			this.revision = null;
		}
	}

	@NonNull
	@Override
	public PerforceScm build() {
		if (head() instanceof P4ChangeRequestSCMHead) {
			P4ChangeRequestSCMHead perforceTag = (P4ChangeRequestSCMHead) head();
			P4Path path = perforceTag.getPath();
			PerforceScm scm = new PerforceScm(source, path, revision);

			P4Review review = new P4Review(head().getName(), CheckoutStatus.SHELVED);
			scm.setReview(review);
			return scm;
		}
		if (head() instanceof P4GraphRequestSCMHead) {
			P4GraphRequestSCMHead graphTag = (P4GraphRequestSCMHead) head();
			P4Path path = graphTag.getPath();
			PerforceScm scm = new PerforceScm(source, path, revision);
			return scm;
		}
		if (head() instanceof P4Head) {
			P4Head perforceHead = (P4Head) head();
			P4Path path = perforceHead.getPath();
			PerforceScm scm = new PerforceScm(source, path, revision);
			return scm;
		}
		throw new IllegalArgumentException("SCMHead not a Perforce instance!");
	}
}
