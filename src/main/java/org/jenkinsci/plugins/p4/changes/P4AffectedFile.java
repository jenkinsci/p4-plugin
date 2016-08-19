package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;

import hudson.scm.EditType;
import hudson.scm.ChangeLogSet.AffectedFile;

public class P4AffectedFile implements AffectedFile {

	private final IFileSpec item;

	public P4AffectedFile(IFileSpec item) {
		this.item = item;
	}

	@Override
	public String getPath() {
		return item.getDepotPathString();
	}

	@Override
	public EditType getEditType() {
		FileAction action = item.getAction();

		switch (action) {
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
