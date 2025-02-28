package org.jenkinsci.plugins.p4.unshelve;

import com.perforce.p4java.core.IChangelistSummary;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.UnshelveTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class UnshelveBuilder extends Builder {

	private final String shelf;
	private final String resolve;
	private final boolean tidy;
	private final boolean ignoreEmpty;

	private static Logger logger = Logger.getLogger(UnshelveBuilder.class.getName());

	@DataBoundConstructor
	public UnshelveBuilder(String shelf, String resolve, boolean tidy, boolean ignoreEmpty) {
		this.shelf = shelf;
		this.resolve = resolve;
		this.tidy = tidy;
		this.ignoreEmpty = ignoreEmpty;
	}

	@Deprecated
	public UnshelveBuilder(String shelf, String resolve, boolean tidy) {
		this(shelf, resolve, tidy, false);
	}

	@Deprecated
	public UnshelveBuilder(String shelf, String resolve) {
		this(shelf, resolve, false, false);
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getShelf() {
		return shelf;
	}

	public String getResolve() {
		return resolve;
	}

	public boolean isTidy() {
		return tidy;
	}

	public boolean isIgnoreEmpty() {
		return ignoreEmpty;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

		AbstractProject<?, ?> project = build.getParent();
		PerforceScm p4scm = PerforceScm.convertToPerforceScm(project.getScm());
		if (p4scm != null) {
			String credential = p4scm.getCredential();
			Workspace workspace = p4scm.getWorkspace();
			FilePath buildWorkspace = build.getWorkspace();
			try {
				if (buildWorkspace == null) {
					return false;
				}
				return unshelve(build, credential, workspace, buildWorkspace, listener);
			} catch (IOException | InterruptedException e) {
				logger.warning("Unable to Unshelve");
				e.printStackTrace();
			}
		}

		return false;
	}

	protected boolean unshelve(Run<?, ?> run, String credential, Workspace workspace, FilePath buildWorkspace,
	                           TaskListener listener) throws IOException, InterruptedException {

		// Setup Unshelve Task
		UnshelveTask task = new UnshelveTask(credential, run, listener, resolve, tidy);

		// Set workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);

		// Expand shelf ${VAR} as needed and set as LABEL
		String id = ws.getExpand().format(shelf, false);

		//	If settings are set to do nothing if changelist is empty just return true.
		if (ignoreEmpty && (id == null || id.isEmpty())) {
			logger.warning("Shelf list ID is empty or null, we will be skipping this task.");
			return true;
		}

		long change = Long.parseLong(id.trim());
		task.setShelf(change);
		task.setWorkspace(ws);

		boolean result = buildWorkspace.act(task);

		// Jenkins instance is not available on slave machine. Update changelog only after build done
		if (result) {
			TagAction tagAction = run.getAction(TagAction.class);
			updateChangeLog(task, workspace, tagAction, change);
		}

		return result;
	}

	private void updateChangeLog(UnshelveTask task, Workspace workspace, TagAction tagAction, long shelf) {
		try (ClientHelper p4 = new ClientHelper(task.getCredential(), task.getListener(), workspace)) {
			List<P4ChangeEntry> changes = new ArrayList<>();
			changes.add(createP4ChangeEntry(p4, shelf));
			for (P4Ref ref : tagAction.getRefChanges()) {
				changes.add(createP4ChangeEntry(p4, ref.getChange()));
			}
			P4ChangeSet.store(tagAction.getChangelog(), changes);

		} catch (Exception e) {
			String err = "Unable to get full changes: " + e;
			logger.severe(err);
			e.printStackTrace();
		}
	}

	private P4ChangeEntry createP4ChangeEntry(ClientHelper p4, long shelf) throws Exception {
		P4ChangeEntry cl = new P4ChangeEntry();
		IChangelistSummary changelistSummary = p4.getChange(shelf);
		cl.setChange(p4, changelistSummary);
		return cl;
	}

	public static DescriptorImpl descriptor() {
		Jenkins j = Jenkins.get();
		return j.getDescriptorByType(UnshelveBuilder.DescriptorImpl.class);
	}

	@Extension
	@Symbol("unshelve")
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce: Unshelve";
		}

		public static ListBoxModel doFillResolveItems() {
			return new ListBoxModel(new Option("Resolve: None", "none"), new Option("Resolve: Safe (-as)", "as"), new Option("Resolve: Merge (-am)", "am"), new Option("Resolve: Force Merge (-af)", "af"), new Option("Resolve: Yours (-ay) -- keep your edits", "ay"), new Option("Resolve: Theirs (-at) -- keep shelf content", "at"));
		}
	}
}
