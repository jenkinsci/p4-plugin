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
		return switch (fileAction) {
			case ABANDONED -> new EditType("abandoned", "The file was abandoned");
			case ADD, ADDED -> EditType.ADD;
			case BRANCH -> new EditType("branch", "The file was branched");
			case COPY_FROM -> new EditType("copy_from", "The file was copied");
			case DELETE, DELETED -> EditType.DELETE;
			case EDIT, EDIT_FROM, EDIT_IGNORED -> EditType.EDIT;
			case IGNORED -> new EditType("ignored", "The file was ignored");
			case IMPORT -> new EditType("import", "The file was imported");
			case INTEGRATE -> new EditType("integrate", "The file was integrated");
			case MERGE_FROM -> new EditType("merge_from", "The file was merged");
			case MOVE -> new EditType("move", "The file was moved");
			case MOVE_ADD -> new EditType("move_add", "File was moved and added");
			case MOVE_DELETE -> new EditType("move_delete", "File was moved and deleted");
			case PURGE -> new EditType("purge", "The file was purged");
			case REFRESHED -> new EditType("refreshed", "The file was refreshed");
			case REPLACED -> new EditType("replaced", "The file was replaced");
			case RESOLVED -> new EditType("resolved", "The file was resolved");
			case SYNC -> new EditType("sync", "The file was synched");
			case UNKNOWN -> new EditType("unknown", "Unknown");
			case UNRESOLVED -> new EditType("unresolved", "The file was unresolved");
			case UPDATED -> new EditType("updated", "The file was updated");
		};
	}
}
