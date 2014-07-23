package org.jenkinsci.plugins.p4.changes;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.p4.client.ClientHelper;

import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.Label;

public class P4ChangeEntry extends ChangeLogSet.Entry {

	private int FILE_COUNT_LIMIT = 50;

	private String id;
	private User author;
	private Date date;
	private String clientId;
	private String msg;
	private Collection<String> affectedPaths;
	private boolean shelved;
	private boolean label;
	private int fileCount;
	private List<IFileSpec> files;
	private List<IJob> jobs;

	public P4ChangeEntry(P4ChangeSet parent) {
		super();
		setParent(parent);
	}

	public void setChange(ClientHelper p4, int changeId) {
		try {
			Changelist changelist = (Changelist) p4.getChange(changeId);

			// set id
			id = "" + changelist.getId();

			// set author
			String user = changelist.getUsername();
			author = User.get(user);

			// set date of change
			date = changelist.getDate();

			// set client id
			clientId = changelist.getClientId();

			// set display message
			msg = changelist.getDescription();

			// set list of file revisions in change
			if (changelist.getStatus() == ChangelistStatus.PENDING) {
				files = p4.loadShelvedFiles(changeId);
				shelved = true;
			} else {
				files = changelist.getFiles(false);
				shelved = false;
			}
			fileCount = files.size();
			if (files.size() > FILE_COUNT_LIMIT) {
				files = files.subList(0, FILE_COUNT_LIMIT - 1);
			}

			// set list of affected paths
			List<String> affectedPaths = new ArrayList<String>();
			for (IFileSpec item : files) {
				affectedPaths.add(item.getDepotPathString());
			}

			// set list of jobs in change
			this.jobs = changelist.getJobs();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setLabel(ClientHelper p4, String labelId) {
		try {
			label = true;
			Label label = (Label) p4.getLabel(labelId);

			// set id
			id = labelId;

			// set author
			String user = label.getOwnerName();
			author = User.get(user);

			// set date of change
			date = label.getLastAccess();

			// set client id
			clientId = labelId;

			// set display message
			msg = label.getDescription();

			// set list of file revisions in change
			files = p4.getTaggedFiles(labelId);
			fileCount = files.size();
			if (files.size() > FILE_COUNT_LIMIT) {
				files = files.subList(0, FILE_COUNT_LIMIT - 1);
			}

			// set list of affected paths
			List<String> affectedPaths = new ArrayList<String>();
			for (IFileSpec item : files) {
				affectedPaths.add(item.getDepotPathString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getId() {
		return id;
	}

	@Override
	public User getAuthor() {
		return author;
	}

	public Date getDate() {
		return date;
	}

	public String getClientId() {
		return clientId;
	}

	@Override
	public String getMsg() {
		return msg;
	}

	@Override
	public Collection<String> getAffectedPaths() {
		return affectedPaths;
	}

	public int getFileCount() {
		return fileCount;
	}

	public List<IFileSpec> getFiles() {
		return files;
	}

	public String getAction(IFileSpec file) {
		FileAction action = file.getAction();
		String s = action.name();
		return s.replace("/", "_");
	}

	public boolean isShelved() {
		return shelved;
	}

	public boolean isLabel() {
		return label;
	}

	public List<IJob> getJobs() {
		return jobs;
	}

	public String getJobStatus(IJob job) {
		Map<String, Object> map = job.getRawFields();
		String status = (String) map.get("Status");
		return status;
	}

	public String getJobSummary(IJob job) {
		String summary = job.getDescription();
		if (summary.length() > 80) {
			summary = summary.substring(0, 80) + "...";
		}
		return summary;
	}
}
