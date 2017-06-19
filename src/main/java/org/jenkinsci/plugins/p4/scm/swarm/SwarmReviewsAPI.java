package org.jenkinsci.plugins.p4.scm.swarm;

import java.util.List;

public class SwarmReviewsAPI {

	private List<Reviews> reviews;

	public static class Reviews {
		private long id;
		private List<Long> changes;

		public long getId() {
			return id;
		}

		public List<Long> getChanges() {
			return changes;
		}

		public Reviews(long id, List<Long> changes) {
			this.id = id;
			this.changes = changes;
		}
	}

	public SwarmReviewsAPI() {
	}

	public SwarmReviewsAPI(List<Reviews> reviews) {
		this.reviews = reviews;
	}

	public List<Reviews> getReviews() {
		return reviews;
	}
}
