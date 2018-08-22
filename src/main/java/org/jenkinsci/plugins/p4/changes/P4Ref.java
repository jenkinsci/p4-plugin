package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.file.IFileSpec;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

import java.io.Serializable;
import java.util.List;

public interface P4Ref extends Serializable, Comparable {

	P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception;

	boolean isLabel();

	boolean isCommit();

	long getChange();

	List<IFileSpec> getFiles(ConnectionHelper p4, int limit) throws Exception;

	String toString();
}
