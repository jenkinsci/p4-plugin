package org.jenkinsci.plugins.p4.swarmAPI;

import java.util.List;

public class SwarmReviewsAPI {

	private List<Reviews> reviews;

	public static class Reviews {
		private long id;
		private List<Long> changes;
		private String author;

		public long getId() {
			return id;
		}

		public List<Long> getChanges() {
			return changes;
		}

		public String getAuthor() {
			return author;
		}

		public Reviews(long id, List<Long> changes, String author) {
			this.id = id;
			this.changes = changes;
			this.author = author;
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
