package org.jenkinsci.plugins.p4.swarmAPI;

import java.util.ArrayList;
import java.util.List;

public class SwarmProjectsAPI {

	private List<SwarmProjectAPI.Project> projects;

	public SwarmProjectsAPI() {
	}

	public List<SwarmProjectAPI.Project> getProjects() {
		return projects;
	}

	public List<String> getIDs() {
		List<String> list = new ArrayList<>();

		for(SwarmProjectAPI.Project p : projects) {
			list.add(p.getId());
		}
		return list;
	}
}
