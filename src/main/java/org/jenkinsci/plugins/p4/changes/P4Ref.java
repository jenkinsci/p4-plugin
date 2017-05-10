package org.jenkinsci.plugins.p4.changes;

import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.Serializable;

public interface P4Ref extends Serializable, Comparable {

	P4ChangeEntry getChangeEntry(ClientHelper p4) throws Exception;

	boolean isLabel();

	boolean isCommit();

	int getChange();

	String toString();
}
