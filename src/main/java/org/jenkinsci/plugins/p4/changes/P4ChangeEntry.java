package org.jenkinsci.plugins.p4.changes;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.export.Exported;

import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.Label;

public class P4ChangeEntry extends ChangeLogSet.Entry {

	private int FILE_COUNT_LIMIT = 50;

	private Object id;
	private User author;
	private Date date = new Date();
	private String clientId = "";
	private String msg = "";
	private Collection<String> affectedPaths;
	private boolean shelved;
	private boolean label;
	private boolean fileLimit = false;
	public List<IFileSpec> files;
	private List<IJob> jobs;

	public P4ChangeEntry(P4ChangeSet parent) {
		super();
		setParent(parent);

		files = new ArrayList<IFileSpec>();
		jobs = new ArrayList<IJob>();
		affectedPaths = new ArrayList<String>();
	}

	public P4ChangeEntry() {

	}

	public void setChange(ConnectionHelper p4, int changeId) throws Exception {
		Changelist changelist = (Changelist) p4.getChange(changeId);

		// set id
		id = changelist.getId();

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
			files = p4.getShelvedFiles(changeId);
			shelved = true;
		} else {
			files = p4.getChangeFiles(changeId);
			shelved = false;
		}
		if (files.size() > FILE_COUNT_LIMIT) {
			fileLimit = true;
			files = files.subList(0, FILE_COUNT_LIMIT);
		}

		// set list of affected paths
		affectedPaths = new ArrayList<String>();
		for (IFileSpec item : files) {
			affectedPaths.add(item.getDepotPathString());
		}

		// set list of jobs in change
		this.jobs = changelist.getJobs();
	}

	public void setLabel(ConnectionHelper p4, String labelId) throws Exception {
		label = true;
		Label label = (Label) p4.getLabel(labelId);

		// set id
		id = labelId;

		// set author
		String user = label.getOwnerName();
		user = (user != null && !user.isEmpty()) ? user : "unknown";
		author = User.get(user);

		// set date of change
		date = label.getLastAccess();

		// set client id
		clientId = labelId;

		// set display message
		msg = label.getDescription();

		// set list of file revisions in change
		files = p4.getLabelFiles(labelId, FILE_COUNT_LIMIT + 1);
		if (files.size() > FILE_COUNT_LIMIT) {
			fileLimit = true;
			files = files.subList(0, FILE_COUNT_LIMIT);
		}

		// set list of affected paths
		affectedPaths = new ArrayList<String>();
		for (IFileSpec item : files) {
			affectedPaths.add(item.getDepotPathString());
		}
	}

	@Exported
	public Object getChangeNumber() {
		return id;
	}

	@Exported
	public String getChangeTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		return sdf.format(date);
	}

	public Object getId() {
		return id;
	}

	public void setId(Object value) {
		id = value;
	}

	@Override
	public User getAuthor() {
		return author;
	}

	public void setAuthor(String value) {
		author = User.get(value);

	}

	public Date getDate() {
		return date;
	}

	public void setDate(String value) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

		try {
			date = sdf.parse(value);
		} catch (ParseException e) {
			// TODO Auto-generated catch block

			e.printStackTrace();
		}
	}

	public void setClientId(String value) {
		clientId = value;
	}

	public String getClientId() {
		return clientId;
	}

	@Override
	public String getMsg() {
		return msg;
	}

	public void setMsg(String value) {
		msg = value;
	}

	@Override
	public Collection<String> getAffectedPaths() {
		return affectedPaths;
	}

	public boolean isFileLimit() {
		return fileLimit;
	}

	public List<IFileSpec> getFiles() {
		return files;
	}

	public String getAction(IFileSpec file) {
		FileAction action = file.getAction();
		String s = action.name();
		return s.replace("/", "_");
	}

	public void setShelved(boolean value) {
		shelved = value;
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

	public int getMaxLimit() {
		return FILE_COUNT_LIMIT;
	}
}
