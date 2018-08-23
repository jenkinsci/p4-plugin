package org.jenkinsci.plugins.p4.scm.events;

import com.perforce.p4java.core.file.IFileSpec;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.ViewMapHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class P4BranchScanner {

	private static Logger logger = Logger.getLogger(P4BranchScanner.class.getName());

	private final P4BaseCredentials credential;
	private final P4Ref change;
	private final String file;

	private String branch = null;
	private String project = null;

	public P4BranchScanner(P4BaseCredentials credential, P4Ref change, String file) {
		this.credential = credential;
		this.change = change;
		this.file = file;

		try {
			scan();
		} catch (Exception e) {
			logger.severe(e.getMessage());
		}
	}

	public String getBranch() {
		return branch;
	}

	public String getProject() {
		return project;
	}

	private void scan() throws Exception {
		try (ConnectionHelper p4 = new ConnectionHelper(credential, getListener())) {
			List<IFileSpec> files = change.getFiles(p4, 1);
			if (files == null || files.isEmpty() || files.get(0) == null) {
				p4.log("BranchScanner: Aborting - empty changelist.");
				return;
			}

			String path = files.get(0).getDepotPathString();
			String[] parts = ViewMapHelper.splitDepotPath(path);
			if (parts.length < 2) {
				p4.log("BranchScanner: Aborting - path too short: " + path);
				return;
			}

			for (int n = parts.length - 1; n >= 1; n--) {
				String[] sub = Arrays.copyOfRange(parts, 0, n);
				String subPath = "//" + String.join("/", sub) + "/" + file;
				if (p4.hasFile(subPath)) {
					branch = sub[n - 1];
					String[] projectSub = Arrays.copyOfRange(parts, 0, n - 1);
					project = "//" + String.join("/", projectSub);
					return;
				}
			}
		}
	}

	private TaskListener getListener() {
		Level level;
		try {
			level = Level.parse(System.getProperty(getClass().getName() + ".defaultListenerLevel", "FINE"));
		} catch (IllegalArgumentException e) {
			level = Level.FINE;
		}
		return new LogTaskListener(Logger.getLogger(getClass().getName()), level);
	}
}
