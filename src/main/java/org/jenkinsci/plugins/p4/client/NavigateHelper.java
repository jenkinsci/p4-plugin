package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.IDepot;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.GetDepotFilesOptions;
import com.perforce.p4java.option.server.GetDirectoriesOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.AutoCompletionCandidates;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NavigateHelper implements Closeable {

	private final int max;
	private final IOptionsServer p4;

	private List<String> paths;

	public NavigateHelper() {
		this(0);
	}

	public NavigateHelper(int max) {
		this.max = max;
		paths = new ArrayList<>();
		p4 = ConnectionFactory.getConnection();
	}

	public AutoCompletionCandidates getCandidates(String value) {
		buildPaths(value);
		return getCandidates();
	}

	public List<String> getPaths(String value) {
		buildPaths(value);
		return paths;
	}

	private void buildPaths(String value) {
		try {
			if (!value.startsWith("//")) {
				value = "//" + value;
			}

			// remove leading '//' markers for depot matching
			String depot = value.substring(2);
			if (!depot.contains("/")) {
				listDepots(depot);
				return;
			}

			listDirs(value);
			listFiles(value);
		} catch (P4JavaException e) {
		}
	}

	private void listDepots(String value) throws P4JavaException {
		if (p4 != null) {
			List<IDepot> list = p4.getDepots();
			for (IDepot l : list) {
				if (l.getName().startsWith(value)) {
					paths.add("//" + l.getName());
				}
			}
		}
	}

	private void listDirs(String value) throws P4JavaException {
		if (p4 != null && value.length() > 4) {

			List<IFileSpec> dirs;
			dirs = FileSpecBuilder.makeFileSpecList(value + "*");
			GetDirectoriesOptions opts = new GetDirectoriesOptions();
			List<IFileSpec> list = p4.getDirectories(dirs, opts);

			if (list == null) {
				return;
			}
			if (max > 0 && list.size() > max) {
				list = list.subList(0, max);
			}

			for (IFileSpec l : list) {
				String dir = l.getOriginalPathString();
				if (dir != null) {
					paths.add(dir);
				}
			}
		}
	}

	private void listFiles(String value) throws P4JavaException {
		if (p4 != null && value.length() > 4) {

			List<IFileSpec> files;
			files = FileSpecBuilder.makeFileSpecList(value + "...");

			GetDepotFilesOptions opts = new GetDepotFilesOptions();
			if (max > 0) {
				opts.setMaxResults(max);
			}

			List<IFileSpec> list = p4.getDepotFiles(files, opts);

			for (IFileSpec l : list) {
				paths.add(l.getDepotPathString());
			}
		}
	}

	private AutoCompletionCandidates getCandidates() {
		AutoCompletionCandidates c = new AutoCompletionCandidates();
		for (String path : paths) {
			c.add(path);
		}
		return c;
	}

	@Override
	public void close() throws IOException {
		try {
			p4.disconnect();
		} catch (P4JavaException e) {
			throw new IOException(e);
		}
	}
}
