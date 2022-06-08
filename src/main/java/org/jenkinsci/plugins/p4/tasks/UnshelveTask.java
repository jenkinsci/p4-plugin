package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.core.IChangelistSummary;
import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class UnshelveTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(UnshelveTask.class.getName());

	private final String resolve;
	private final boolean tidy;
	private long shelf;
	private final TagAction tagAction;

	public UnshelveTask(String credential, Run<?, ?> run, TaskListener listener, String resolve, boolean tidy) {
		super(credential, run, listener);
		this.resolve = resolve;
		this.tidy = tidy;
		tagAction = run.getAction(TagAction.class);
	}

	public void setShelf(long shelf) {
		this.shelf = shelf;
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}

			p4.unshelveFiles(shelf);

			updateChangeLogFile(p4);

			p4.resolveFiles(resolve);

			if(tidy) {
				p4.revertAllFiles(true);
			}

		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String err = "Unable to unshelve change: " + e;
			p4.log(err);
			logger.warning(err);
			throw new AbortException(err);
		}
		return true;
	}

	private void updateChangeLogFile(ClientHelper p4) throws Exception {
		List<P4ChangeEntry> changes = new ArrayList<>();
		changes.add(createP4ChangeEntry(p4, shelf));
		for (P4Ref ref : tagAction.getRefChanges()) {
			changes.add(createP4ChangeEntry(p4, ref.getChange()));
		}
		P4ChangeSet.store(tagAction.getChangelog(), changes);
	}

	private P4ChangeEntry createP4ChangeEntry(ClientHelper p4, long shelf) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		IChangelistSummary changelistSummary = p4.getChange(shelf);
		cl.setChange(p4, changelistSummary);
		return cl;
	}

	@Override
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

}
