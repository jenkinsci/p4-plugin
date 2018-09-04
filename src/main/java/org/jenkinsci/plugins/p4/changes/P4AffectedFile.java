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
		return action.getName().toUpperCase();
	}

	private EditType parseFileAction(FileAction fileAction) {
		switch (fileAction) {
		case ABANDONED:
			return new EditType("abandoned","The file was abandoned");
		case ADD:
		case ADDED:
			return EditType.ADD;
		case BRANCH:
			return new EditType("branch", "The file was branched");
		case COPY_FROM:
			return new EditType("copy_from", "The file was copied");
		case DELETE:
		case DELETED:
			return EditType.DELETE;
		case EDIT:
		case EDIT_FROM:
		case EDIT_IGNORED:
			return EditType.EDIT;
		case IGNORED:
			return new EditType("ignored", "The file was ignored");
		case IMPORT:
			return new EditType("import", "The file was imported");
		case INTEGRATE:
			return new EditType("integrate", "The file was integrated");
		case MERGE_FROM:
			return new EditType("merge_from", "The file was merged");
		case MOVE:
			return new EditType("move", "The file was moved");
		case MOVE_ADD:
			return new EditType("move_add", "File was moved and added");
		case MOVE_DELETE:
			return new EditType("move_delete", "File was moved and deleted");
		case PURGE:
			return new EditType("purge", "The file was purged");
		case REFRESHED:
			return new EditType("refreshed", "The file was refreshed");
		case REPLACED:
			return new EditType("replaced", "The file was replaced");
		case RESOLVED:
			return new EditType("resolved", "The file was resolved");
		case SYNC:
			return new EditType("sync", "The file was synched");
		case UNKNOWN:
			return new EditType("unknown", "Unknown");
		case UNRESOLVED:
			return new EditType("unresolved", "The file was unresolved");
		case UPDATED:
			return new EditType("updated", "The file was updated");
		default:
			return EditType.EDIT;
		}
	}
}
