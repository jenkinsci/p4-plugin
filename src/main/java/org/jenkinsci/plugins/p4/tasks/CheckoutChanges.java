package org.jenkinsci.plugins.p4.tasks;

import hudson.model.TaskListener;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Changelist;

public class CheckoutChanges {

	private String credential;
	private TaskListener listener;
	private String client;

	private String pin;
	private List<Filter> filter;
	private boolean perChange;

	List<Integer> changes = new ArrayList<Integer>();

	public CheckoutChanges(String credential, TaskListener listener,
			String client) {
		this.credential = credential;
		this.listener = listener;
		this.client = client;
	}

	public void setLimit(String expandedPin) {
		pin = expandedPin;
	}

	public void setFilter(List<Filter> pollFilters) {
		filter = pollFilters;
		perChange = false;

		if (filter != null) {
			for (Filter f : filter) {
				if (f instanceof FilterPerChangeImpl) {
					if (((FilterPerChangeImpl) f).isPerChange()) {
						perChange = true;
					}
				}
			}
		}
	}

	public List<Integer> getChanges() {
		return changes;
	}

	public void process() {
		ClientHelper p4 = new ClientHelper(credential, listener, client);
		PrintStream log = listener.getLogger();

		// find changes...
		try {
			if (pin != null && !pin.isEmpty()) {
				List<Integer> have = p4.listHaveChanges(pin);
				int last = 0;
				if (!have.isEmpty()) {
					last = have.get(have.size() - 1);
				}
				log.println("P4: Polling with label/change: " + last + ","
						+ pin);
				changes = p4.listChanges(last, pin);
			} else {
				List<Integer> have = p4.listHaveChanges();
				int last = 0;
				if (!have.isEmpty()) {
					last = have.get(have.size() - 1);
				}
				log.println("P4: Polling with label/change: " + last + ",now");
				changes = p4.listChanges(last);
			}
		} catch (Exception e) {
			log.println("P4: Unable to fetch changes:" + e);
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
					log.println("... found change: " + changelist.getId());
				}
			}
			changes = remainder;
		} catch (Exception e) {
			log.println("P4: Unable to filter changes:" + e);
		} finally {
			p4.disconnect();
		}

		// if build per change...
		if (!changes.isEmpty() && perChange) {
			int lowest = changes.get(changes.size() - 1);
			changes = Arrays.asList(lowest);
			log.println("next change: " + lowest);
		}
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
