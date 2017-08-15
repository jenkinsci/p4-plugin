package org.jenkinsci.plugins.p4.scm.swarm;

import java.util.ArrayList;
import java.util.List;

public class SwarmProjectAPI {

	private Project project;

	public SwarmProjectAPI() {
	}

	public SwarmProjectAPI(Project project) {
		this.project = project;
	}

	public Project getProject() {
		return project;
	}

	public static class Project {
		private List<Branch> branches;

		public Project(List<Branch> branches) {
			this.branches = branches;
		}

		public List<Branch> getBranches() {
			return branches;
		}
	}

	public static class Branch {
		private String id;
		private String name;
		private List<String> paths;

		public Branch(String id, String name, List<String> paths) {
			this.id = id;
			this.name = name;
			this.paths = paths;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		/**
		 * Return list of paths from a branch, but strip '/...'
		 *
		 * @return List of paths
		 */
		public List<P4Path> getPaths() {
			List<P4Path> list = new ArrayList<>();
			for(String path : paths) {
				if (path.endsWith("/...")) {
					path = path.substring(0, path.lastIndexOf("/..."));
				}
				list.add(new P4Path(path));
			}
			return list;
		}
	}
}
