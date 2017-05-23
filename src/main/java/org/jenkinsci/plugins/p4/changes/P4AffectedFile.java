package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.EditType;

public class P4AffectedFile implements AffectedFile {

	private final String path;
	private final String revision;
	private final EditType action;

	public P4AffectedFile(IFileSpec item) {
		this.path = item.getDepotPathString();
		this.revision = "#" + item.getEndRevision();
		this.action = parseFileAction(item.getAction());
	}

	public P4AffectedFile(String path, String revision, FileAction action) {
		this.path = path;
		this.revision = revision;
		this.action = parseFileAction(action);
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public EditType getEditType() {
		return action;
	}

	public String getRevision() {
		return revision;
	}

	public String getAction() {
		return action.getName();
	}

	private EditType parseFileAction(FileAction fileAction) {
		switch (fileAction) {
		case ADD:
		case MOVE_ADD:
			return EditType.ADD;

		case EDIT:
			return EditType.EDIT;

		case DELETE:
		case MOVE_DELETE:
			return EditType.DELETE;

		default:
			return EditType.EDIT;
		}
	}
}
