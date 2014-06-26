package org.jenkinsci.plugins.p4_client.client;

import hudson.model.AutoCompletionCandidates;

import java.util.List;

import com.perforce.p4java.core.IDepot;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.option.server.GetDirectoriesOptions;
import com.perforce.p4java.server.IOptionsServer;

public class NavigateHelper {

	public static AutoCompletionCandidates getPath(String value) {

		if (!value.startsWith("//")) {
			return null;
		}

		// remove leading '//' markers for depot matching
		String depot = value.substring(2);
		if (!depot.contains("/")) {
			return listDepots(depot);
		}

		AutoCompletionCandidates c = new AutoCompletionCandidates();
		listDirs(value, c);
		listFiles(value, c);
		return c;
	}

	private static AutoCompletionCandidates listDepots(String value) {
		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null) {
				List<IDepot> list = iserver.getDepots();
				for (IDepot l : list) {
					if (l.getName().startsWith(value)) {
						c.add("//" + l.getName());
					}
				}
			}
		} catch (Exception e) {
		}
		return c;
	}

	private static void listDirs(String value, AutoCompletionCandidates c) {
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 4) {

				List<IFileSpec> dirs;
				dirs = FileSpecBuilder.makeFileSpecList(value + "*");
				GetDirectoriesOptions opts = new GetDirectoriesOptions();
				List<IFileSpec> list = iserver.getDirectories(dirs, opts);

				if (list == null) {
					return;
				}
				if (list.size() > 10) {
					list = list.subList(0, 10);
				}
				for (IFileSpec l : list) {
					String dir = l.getOriginalPathString();
					if (dir != null) {
						c.add(dir);
					}
				}
			}
		} catch (Exception e) {
		}
	}

	private static void listFiles(String value, AutoCompletionCandidates c) {
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 4) {

				List<IFileSpec> files;
				files = FileSpecBuilder.makeFileSpecList(value + "...");
				GetDepotFilesOptions opts = new GetDepotFilesOptions();
				opts.setMaxResults(10);
				List<IFileSpec> list = iserver.getDepotFiles(files, opts);

				for (IFileSpec l : list) {
					c.add(l.getDepotPathString());
				}
			}
		} catch (Exception e) {
		}
	}
}
