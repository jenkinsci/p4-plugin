package org.jenkinsci.plugins.p4.swarmAPI;

import java.util.HashMap;
import java.util.List;

public class SwarmReviewAPI {

	private Review review;

	public SwarmReviewAPI() {
	}

	public SwarmReviewAPI(Review review) {
		this.review = review;
	}

	public Review getReview() {
		return review;
	}

	public static class Review {
		private List<Long> changes;
		private List<Long> commits;
		private HashMap<String, List<String>> projects;
		private String author;

		public Review(List<Long> changes, List<Long> commits, HashMap<String, List<String>> projects, String author) {
			this.changes = changes;
			this.commits = commits;
			this.projects = projects;
			this.author = author;
		}

		public String getAuthor() {
			return author;
		}

		public List<Long> getChanges() {
			return changes;
		}

		public List<Long> getCommits() {
			return commits;
		}

		public HashMap<String, List<String>> getProjects() {
			return projects;
		}
	}
}
