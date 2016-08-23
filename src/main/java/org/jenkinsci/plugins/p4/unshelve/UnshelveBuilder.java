package org.jenkinsci.plugins.p4.unshelve;

import java.io.IOException;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.tasks.UnshelveTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;

public class UnshelveBuilder extends Builder {

	private final String shelf;
	private final String resolve;

	private static Logger logger = Logger.getLogger(UnshelveBuilder.class.getName());

	@DataBoundConstructor
	public UnshelveBuilder(String shelf, String resolve) {
		this.shelf = shelf;
		this.resolve = resolve;
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
			} catch (IOException e) {
				logger.warning("Unable to Unshelve");
				e.printStackTrace();
			} catch (InterruptedException e) {
				logger.warning("Unable to Unshelve");
				e.printStackTrace();
			}
		}

		return false;
	}

	protected boolean unshelve(Run<?, ?> run, String credential, Workspace workspace, FilePath buildWorkspace,
			TaskListener listener) throws IOException, InterruptedException {

		// Setup Unshelve Task
		UnshelveTask task = new UnshelveTask(resolve);
		task.setListener(listener);
		task.setCredential(credential);

		// Set workspace used for the Task
		Workspace ws = task.setEnvironment(run, workspace, buildWorkspace);

		// Expand shelf ${VAR} as needed and set as LABEL
		String id = ws.getExpand().format(shelf, false);
		int change = Integer.parseInt(id);
		task.setShelf(change);
		task.setWorkspace(ws);

		return buildWorkspace.act(task);
	}

	public static DescriptorImpl descriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.getDescriptorByType(UnshelveBuilder.DescriptorImpl.class);
		}
		return null;
	}
        

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Perforce: Unshelve";
		}
                
                public ListBoxModel doFillResolveItems() {
                    return new ListBoxModel(new Option("Resolve: None", "none"),
                                            new Option("Resolve: Safe (-as)", "as"),
                                            new Option("Resolve: Merge (-am)", "am"),
                                            new Option("Resolve: Force Merge (-af)", "af"),
                                            new Option("Resolve: Yours (-ay) -- keep your edits", "ay"),
                                            new Option("Resolve: Merge (Resolve: Theirs (-at) -- keep shelf content)", "at"));       
                }
	}
}
