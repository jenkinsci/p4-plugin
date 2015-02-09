package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jenkins.security.Roles;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Changelist;

public class PollTask extends AbstractTask implements
		FileCallable<List<Integer>>, RoleSensitive, Serializable {

	private static final long serialVersionUID = 1L;

	private final List<Filter> filter;
	private final boolean perChange;

	private String pin;

	public PollTask(List<Filter> filter) {
		this.filter = filter;

		// look for incremental filter option
		boolean incremental = false;
		if (filter != null) {
			for (Filter f : filter) {
				if (f instanceof FilterPerChangeImpl) {
					if (((FilterPerChangeImpl) f).isPerChange()) {
						incremental = true;
					}
				}
			}
		}
		this.perChange = incremental;
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

	public List<Integer> invoke(File f, VirtualChannel channel)
			throws IOException, InterruptedException {
		List<Integer> changes = new ArrayList<Integer>();

		ClientHelper p4 = getConnection();
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return changes;
			}

			// find changes...
			if (pin != null && !pin.isEmpty()) {
				List<Integer> have = p4.listHaveChanges(pin);
				int last = 0;
				if (!have.isEmpty()) {
					last = have.get(have.size() - 1);
				}
				p4.log("P4: Polling with label/change: " + last + "," + pin);
				changes = p4.listChanges(last, pin);
			} else {
				List<Integer> have = p4.listHaveChanges();
				int last = 0;
				if (!have.isEmpty()) {
					last = have.get(have.size() - 1);
				}
				p4.log("P4: Polling with label/change: " + last + ",now");
				changes = p4.listChanges(last);
			}
		} catch (Exception e) {
			p4.log("P4: Unable to fetch changes:" + e);
			p4.disconnect();
		}

		// filter changes...
		try {
			List<Integer> remainder = new ArrayList<Integer>();
			for (int c : changes) {
				Changelist changelist = p4.getChange(c);
				// add unfiltered changes to remainder list
				if (!filterChange(changelist, filter)) {
					remainder.add(changelist.getId());
					p4.log("... found change: " + changelist.getId());
				}
			}
			changes = remainder;
		} catch (Exception e) {
			p4.log("P4: Unable to filter changes:" + e);
		} finally {
			p4.disconnect();
		}

		// if build per change...
		if (!changes.isEmpty() && perChange) {
			int lowest = changes.get(changes.size() - 1);
			changes = Arrays.asList(lowest);
			p4.log("next change: " + lowest);
		}
		return changes;
	}

	public void setLimit(String expandedPin) {
		pin = expandedPin;
	}

	/**
	 * Returns true if change should be filtered
	 * 
	 * @param changelist
	 * @return
	 * @throws AccessException
	 * @throws RequestException
	 * @throws Exception
	 */
	private boolean filterChange(Changelist changelist, List<Filter> scmFilter)
			throws Exception {
		// exit early if no filters
		if (scmFilter == null) {
			return false;
		}

		String user = changelist.getUsername();
		List<IFileSpec> files = changelist.getFiles(true);

		for (Filter f : scmFilter) {
			// Scan through User filters
			if (f instanceof FilterUserImpl) {
				// return is user matches filter
				String u = ((FilterUserImpl) f).getUser();
				if (u.equalsIgnoreCase(user)) {
					return true;
				}
			}

			// Scan through Path filters
			if (f instanceof FilterPathImpl) {
				// add unmatched files to remainder list
				List<IFileSpec> remainder = new ArrayList<IFileSpec>();
				String path = ((FilterPathImpl) f).getPath();
				for (IFileSpec s : files) {
					String p = s.getDepotPathString();
					if (!p.startsWith(path)) {
						remainder.add(s);
					}
				}

				// update files with remainder
				files = remainder;

				// add if all files are removed then remove change
				if (files.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}
}
