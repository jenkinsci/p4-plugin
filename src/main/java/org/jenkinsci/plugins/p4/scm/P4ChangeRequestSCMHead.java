package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.review.P4Review;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;

import java.io.Serial;

public class P4ChangeRequestSCMHead extends P4SCMHead implements ChangeRequestSCMHead, ChangeRequestSCMHead2 {

	@Serial
	private static final long serialVersionUID = 1L;

	private final SCMHead target;
	private final String review;
	private final String author;

	P4ChangeRequestSCMHead(String name, String review, P4Path path, SCMHead target) {
		this(name, review, path, target, null);
	}

	P4ChangeRequestSCMHead(String name, String review, P4Path path, SCMHead target, String author) {
		super(name, path);
		this.target = target;
		this.review = review;
		this.author = author;
	}

	@NonNull
	@Override
	public String getId() {
		return getName();
	}

	public String getReview() {
		return review;
	}

	public String getAuthor() {
		return author;
	}

	/**
	 * Branch to which this change would be merged or applied if it were accepted.
	 *
	 * @return a “target” or “base” branch
	 */
	@NonNull
	@Override
	public SCMHead getTarget() {
		return target;
	}

	@Override
	public PerforceScm getScm(AbstractP4ScmSource source, P4Path path, P4Ref revision) {
		PerforceScm scm = new PerforceScm(source, path, revision);
		P4Review review = new P4Review(getName(), CheckoutStatus.SHELVED);
		scm.setReview(review);
		return scm;
	}

	@NonNull
	@Override
	public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
		return ChangeRequestCheckoutStrategy.MERGE;
	}

	@NonNull
	@Override
	public String getOriginName() {
		return ((P4SCMHead) getTarget()).getPath().getPath();
	}
}
