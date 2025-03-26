package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.graph.ICommit;
import com.perforce.p4java.impl.generic.core.Label;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer.UserProperty;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.email.P4UserProperty;
import org.kohsuke.stapler.export.Exported;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class P4ChangeEntry extends ChangeLogSet.Entry {

	private static Logger logger = Logger.getLogger(P4ChangeEntry.class.getName());

	private int fileCountLimit = PerforceScm.DEFAULT_FILE_LIMIT;

	private P4Ref id;

	private User author;
	private Date date = new Date();
	private String clientId = "";
	private String msg = "";

	private List<P4AffectedFile> affectedFiles;
	private boolean shelved;

	private boolean fileLimit = false;
	private List<IFix> jobs;

	public P4ChangeEntry(P4ChangeSet parent) {
		super();
		setParent(parent);

		jobs = new ArrayList<>();
		affectedFiles = new ArrayList<>();
		getFileCountLimit();
	}

	public P4ChangeEntry() {
		getFileCountLimit();
	}

	public void setChange(ConnectionHelper p4, IChangelistSummary changelist) throws Exception {

		// set id
		int changeId = changelist.getId();
		id = new P4ChangeRef(changeId);

		// set author
		String user = changelist.getUsername();
		author = User.getOrCreateByIdOrFullName(user);

		// set email property on user
		String email = p4.getEmail(user);
		if (email != null && !email.isEmpty()) {
			P4UserProperty p4prop = new P4UserProperty(email);
			author.addProperty(p4prop);
			logger.fine("Setting email for user: " + user + ":" + email);

			// Set default email for Jenkins user if not defined
			UserProperty prop = author.getProperty(UserProperty.class);
			if (prop == null || prop.getAddress() == null || prop.getAddress().isEmpty()) {
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
		List<IFileSpec> files;
		if (changelist.getStatus() == ChangelistStatus.PENDING) {
			files = p4.getShelvedFiles(changeId);
			shelved = true;
		} else {
			files = p4.getChangeFiles(changeId, fileCountLimit + 1);
			shelved = false;
		}
		if (files != null && files.size() > fileCountLimit) {
			fileLimit = true;
			files = files.subList(0, fileCountLimit);
		}

		// set list of affected paths/files
		affectedFiles = new ArrayList<>();
		if (files != null) {
			for (IFileSpec item : files) {
				affectedFiles.add(new P4AffectedFile(item));
			}
		}

		// set list of jobs in change
		this.jobs = p4.getJobs(changeId);
	}

	public void setLabel(ConnectionHelper p4, String labelId) throws Exception {
		Label label = p4.getLabel(labelId);

		// set id
		id = new P4LabelRef(labelId);

		// set author
		String user = label.getOwnerName();
		user = (user != null && !user.isEmpty()) ? user : "unknown";
		author = User.getOrCreateByIdOrFullName(user);

		// set date of change
		date = label.getLastAccess();

		// set client id
		clientId = labelId;

		// set display message
		msg = label.getDescription();

		// set list of file revisions in change
		List<IFileSpec> files = p4.getLabelFiles(labelId, fileCountLimit + 1);
		if (files.size() > fileCountLimit) {
			fileLimit = true;
			files = files.subList(0, fileCountLimit);
		}

		// set list of affected files
		affectedFiles = new ArrayList<>();
		for (IFileSpec item : files) {
			affectedFiles.add(new P4AffectedFile(item));
		}
	}

	public void setGraphCommit(ConnectionHelper p4, String id) throws Exception {
		if (id == null || id.isEmpty() || !id.contains("@")) {
			return;
		}

		String[] parts = id.split("@");
		if (parts.length != 2) {
			return;
		}

		String repo = parts[0];
		String sha = parts[1];
		setGraphCommit(p4, repo, sha);
	}

	public void setGraphCommit(ConnectionHelper p4, String repo, String sha) throws Exception {

		ICommit commit = p4.getGraphCommit(sha, repo);
		id = new P4GraphRef(repo, commit);

		// set author
		String user = commit.getAuthor();
		user = (user != null && !user.isEmpty()) ? user : "unknown";
		author = User.getOrCreateByIdOrFullName(user);

		// set date of change
		date = commit.getDate();

		// set client id
		clientId = commit.getAuthorEmail();

		// set display message
		msg = commit.getDescription();

		// set list of affected paths
		affectedFiles = new ArrayList<>();

		List<IFileSpec> graphFiles = p4.getCommitFiles(repo, sha);
		for (IFileSpec item : graphFiles) {
			String path = item.getDepotPathString();
			FileAction action = item.getAction();
			affectedFiles.add(new P4AffectedFile(path, sha, action));
		}

		if (affectedFiles.size() > fileCountLimit) {
			fileLimit = true;
			affectedFiles = affectedFiles.subList(0, fileCountLimit);
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

	public P4Ref getId() {
		return id;
	}

	public void setId(P4Ref value) {
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
		author = User.getOrCreateByIdOrFullName(value);
	}

	public Date getDate() {
		return (Date) date.clone();
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

	public int getRows() {
		String[] lines = msg.split("\r\n|\r|\n");
		int rows = lines.length;
		rows = Math.min(rows, 10);
		return rows;
	}

	public void setMsg(String value) {
		msg = value;
	}

	// JENKINS-31306
	@Override
	public Collection<String> getAffectedPaths() {
		Collection<String> affectedPaths = new ArrayList<>();
		for (P4AffectedFile item : getAffectedFiles()) {
			affectedPaths.add(item.getPath());
		}

		return affectedPaths;
	}

	@Override
	public Collection<P4AffectedFile> getAffectedFiles() {
		return affectedFiles;
	}

	public void addAffectedFiles(P4AffectedFile file) {
		affectedFiles.add(file);
	}

	public void setFileLimit(boolean value) {
		fileLimit = value;
	}

	public boolean isFileLimit() {
		return fileLimit;
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

	public void addJob(IFix job) {
		jobs.add(job);
	}

	public String getJobStatus(IFix job) {
		String status = job.getStatus();
		return status;
	}

	public int getMaxLimit() {
		return fileCountLimit;
	}

	// For email-ext
	@Exported
	public long getTimestamp() {
		return getDate().getTime();
	}

	// For email-ext
	@Exported
	public String getCommitId() {
		return getChangeNumber();
	}

	private int getFileCountLimit() {
		int max = 0;
		Jenkins j = Jenkins.get();
		if (j != null) {
			Descriptor dsc = j.getDescriptor(PerforceScm.class);
			if (dsc instanceof PerforceScm.DescriptorImpl p4scm) {
				max = p4scm.getMaxFiles();
			}
		}
		fileCountLimit = (max > 0) ? max : PerforceScm.DEFAULT_FILE_LIMIT;
		return fileCountLimit;
	}
}
