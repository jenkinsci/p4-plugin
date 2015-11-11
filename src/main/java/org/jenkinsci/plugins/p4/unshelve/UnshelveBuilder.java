package org.jenkinsci.plugins.p4.unshelve;

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
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

public class UnshelveBuilder extends Builder {

	private final String shelf;
	private final String resolve;

	private static Logger logger = Logger.getLogger(UnshelveBuilder.class.getName());

	@DataBoundConstructor
	public UnshelveBuilder(String shelf, String resolve) {
		this.shelf = shelf;
		this.resolve = resolve;
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
		SCM scm = project.getScm();

		if (scm instanceof PerforceScm) {
			PerforceScm p4 = (PerforceScm) scm;
			String credential = p4.getCredential();
			Workspace workspace = p4.getWorkspace();

			// Setup Unshelve Task
			FilePath buildWorkspace = build.getWorkspace();
			UnshelveTask task = new UnshelveTask(resolve);
			task.setListener(listener);
			task.setCredential(credential);

			// Set workspace used for the Task
			try {
				Workspace ws = task.setEnvironment(build, workspace, buildWorkspace);

				// Expand shelf ${VAR} as needed and set as LABEL
				String id = ws.getExpand().format(shelf, false);
				int change = Integer.parseInt(id);
				task.setShelf(change);
				task.setWorkspace(ws);

				return buildWorkspace.act(task);

			} catch (Exception e) {
				logger.warning("Unable to Unshelve");
				e.printStackTrace();
			}
		}
		return false;
	}

	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {

		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Perforce: Unshelve";
		}
	}
}
