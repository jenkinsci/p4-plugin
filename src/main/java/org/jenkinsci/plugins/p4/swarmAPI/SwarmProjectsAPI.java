package org.jenkinsci.plugins.p4.swarmAPI;

import java.util.ArrayList;
import java.util.List;

public class SwarmProjectsAPI {

	private List<SwarmProjectAPI.Project> projects = new ArrayList<>();

	public SwarmProjectsAPI() {
	}

	public List<SwarmProjectAPI.Project> getProjects() {
		return projects;
	}

	/**
	 * Filtered list of project IDs by owner and member.
	 *
	 * @param user Perforce/Swarm user
	 * @return a list of projects.
	 */
	public List<String> getIDsByUser(String user) {
		List<String> list = new ArrayList<>();

		for (SwarmProjectAPI.Project p : projects) {
			if (p.getMembers().contains(user) || p.getOwners().contains(user)) {
				list.add(p.getId());
			}
		}
		return list;
	}
}
