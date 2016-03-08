package org.jenkinsci.plugins.p4.changes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.email.P4UserProperty;
import org.kohsuke.stapler.export.Exported;

import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Label;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer.UserProperty;

public class P4ChangeEntry extends ChangeLogSet.Entry {

	private static Logger logger = Logger.getLogger(P4ChangeEntry.class.getName());

	private int FILE_COUNT_LIMIT = 50;

	private P4Revision id;

	private User author;
	private Date date = new Date();
	private String clientId = "";
	private String msg = "";
	private Collection<String> affectedPaths;
	private boolean shelved;

	private boolean fileLimit = false;
	public List<IFileSpec> files;
	public List<IFix> jobs;

	public P4ChangeEntry(P4ChangeSet parent) {
		super();
		setParent(parent);

		files = new ArrayList<IFileSpec>();
		jobs = new ArrayList<IFix>();
		affectedPaths = new ArrayList<String>();
	}

	public P4ChangeEntry() {

	}

	public void setChange(ConnectionHelper p4, IChangelistSummary changelist) throws Exception {

		// set id
		int changeId = changelist.getId();
		id = new P4Revision(changeId);

		// set author
		String user = changelist.getUsername();
		author = User.get(user);

		// set email property on user
		String email = p4.getEmail(user);
		if (email != null && !email.isEmpty()) {
			P4UserProperty p4prop = new P4UserProperty(email);
			author.addProperty(p4prop);
			logger.fine("Setting email for user: " + user + ":" + email);
			
			// Set default email for Jenkins user if not defined
			UserProperty prop = author.getProperty(UserProperty.class);
			if( prop == null || prop.getAddress() == null || prop.getAddress().isEmpty()) {
				prop = new UserProperty(email);
				author.addProperty(prop);
				logger.fine("Setting default user: " + user + ":" + email);
			}
		}

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
			files = p4.getChangeFiles(changeId, FILE_COUNT_LIMIT + 1);
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
		this.jobs = p4.getJobs(changeId);
	}

	public void setLabel(ConnectionHelper p4, String labelId) throws Exception {
		Label label = (Label) p4.getLabel(labelId);

		// set id
		id = new P4Revision(labelId);

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
	public String getChangeNumber() {
		return id.toString();
	}

	@Exported
	public String getChangeTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(date);
	}

	public P4Revision getId() {
		return id;
	}

	public void setId(P4Revision value) {
		id = value;
	}

	@Override
	public User getAuthor() {
		// JENKINS-31169
		if (author == null) {
			return User.getUnknown();
		}
		return author;
	}

	public void setAuthor(String value) {
		author = User.get(value);
	}

	public Date getDate() {
		return date;
	}

	public void setDate(String value) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
		// JENKINS-31306
		if (affectedPaths.size() < 1 && files != null && files.size() > 0) {
			for (IFileSpec item : files) {
				affectedPaths.add(item.getDepotPathString());
			}
		}
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
		return id.isLabel();
	}

	public List<IFix> getJobs() {
		return jobs;
	}

	public String getJobStatus(IFix job) {
		String status = job.getStatus();
		return status;
	}

	public int getMaxLimit() {
		return FILE_COUNT_LIMIT;
	}
}
