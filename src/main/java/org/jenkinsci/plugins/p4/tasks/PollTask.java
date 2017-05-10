package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Changelist;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4LabelRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;
import org.jenkinsci.plugins.p4.filters.FilterViewMaskImpl;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PollTask extends AbstractTask implements FileCallable<List<P4Ref>>, Serializable {

	private static final long serialVersionUID = 1L;

	private final List<Filter> filter;
	private final List<P4Ref> lastRefs;

	private String pin;

	public PollTask(List<Filter> filter, List<P4Ref> lastRefs) {
		this.filter = filter;
		this.lastRefs = lastRefs;
	}

	@SuppressWarnings("unchecked")
	public List<P4Ref> invoke(File workspace, VirtualChannel channel) throws IOException {
		return (List<P4Ref>) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		List<P4Ref> changes = new ArrayList<P4Ref>();

		// find changes...
		if (pin != null && !pin.isEmpty()) {
			changes = p4.listHaveChanges(lastRefs, new P4LabelRef(pin));
		} else {
			changes = p4.listHaveChanges(lastRefs);
		}

		// filter changes...
		List<P4Ref> remainder = new ArrayList<P4Ref>();
		for (P4Ref c : changes) {
			int change = c.getChange();
			if (change > 0) {
				Changelist changelist = p4.getChange(change);
				// add unfiltered changes to remainder list
				if (!filterChange(changelist, filter)) {
					remainder.add(new P4ChangeRef(changelist.getId()));
					p4.log("... found change: " + changelist.getId());
				}
			}
		}
		changes = remainder;

		// Poll Graph commit changes
		if (p4.checkVersion(20171)) {
			List<IRepo> repos = p4.listRepos();
			for (IRepo repo : repos) {
				P4Ref graphHead = p4.getGraphHead(repo.getName());
				List<P4Ref> commits = p4.listCommits(lastRefs, graphHead);
				changes.addAll(commits);
			}
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
	 * @throws AccessException
	 * @throws RequestException
	 * @throws Exception
	 */
	private boolean filterChange(Changelist changelist, List<Filter> scmFilter) throws Exception {
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

			// Scan through View Mask filters
			if (f instanceof FilterViewMaskImpl) {
				// at least one file in the change must be contained in the view mask
				List<IFileSpec> included = new ArrayList<IFileSpec>();

				String viewMask = ((FilterViewMaskImpl) f).getViewMask();
				for (IFileSpec s : files) {
					boolean isFileInViewMask = false;
					String p = s.getDepotPathString();
					for (String maskPath : viewMask.split("\n")) {
						if (p.startsWith(maskPath)) {
							isFileInViewMask = true;
						}

						if (maskPath.startsWith("-")) {
							String excludedMaskPath = maskPath.substring(maskPath.indexOf("-") + 1);
							if (p.startsWith(excludedMaskPath)) {
								isFileInViewMask = false;
							}
						}
					}

					if (isFileInViewMask) {
						included.add(s);
					}
				}

				if (included.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}
}
