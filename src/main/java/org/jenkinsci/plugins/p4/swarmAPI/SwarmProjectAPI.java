package org.jenkinsci.plugins.p4.swarmAPI;

import org.jenkinsci.plugins.p4.scm.P4Path;

import java.util.List;

public class SwarmProjectAPI {

	private List<Project> projects;

	public SwarmProjectAPI() {
	}

	public SwarmProjectAPI(List<Project> projects) {
		this.projects = projects;
	}

	public List<Project> getProject() {
		return projects;
	}

	public static class Project {
		private String id;

		private List<String> members;
		private List<String> owners;
		private List<Branch> branches;

		public Project(String id, List<String> members, List<String> owners, List<Branch> branches) {
			this.id = id;
			this.members = members;
			this.owners = owners;
			this.branches = branches;
		}

		public String getId() {
			return id;
		}

		public List<String> getMembers() {
			return members;
		}

		public List<String> getOwners() {
			return owners;
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
		public P4Path getPath() {
			String path = paths.get(0);
			path = path.substring(0, path.lastIndexOf("/..."));
			P4Path p4Path = new P4Path(path);
			p4Path.setMappings(paths);
			return p4Path;
		}

		public List<String> getPaths() {
			return paths;
		}
	}
}
