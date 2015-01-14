package org.jenkinsci.plugins.p4.asset;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.p4.credentials.P4StandardCredentials;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.tasks.PublishTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

public class AssetNotifier extends Notifier {

	private static Logger logger = Logger.getLogger(AssetNotifier.class
			.getName());

	private final String credential;
	private final Workspace workspace;
	private final Publish publish;

	public String getCredential() {
		return credential;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Publish getPublish() {
		return publish;
	}

	@DataBoundConstructor
	public AssetNotifier(String credential, Workspace workspace, Publish publish) {
		this.credential = credential;
		this.workspace = workspace;
		this.publish = publish;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		boolean success = true;

		Workspace ws = (Workspace) workspace.clone();
		try {
			EnvVars envVars = build.getEnvironment(listener);
			ws.clear();
			ws.load(envVars);
			ws.setRootPath(build.getWorkspace().getRemote());
			String desc = publish.getDescription();
			desc = ws.expand(desc, false);
			publish.setExpandedDesc(desc);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Create task
		PublishTask task = new PublishTask(publish);
		task.setCredential(credential);
		task.setWorkspace(ws);
		task.setListener(listener);

		FilePath buildWorkspace = build.getWorkspace();
		success &= buildWorkspace.act(task);

		return success;
	}

	public static DescriptorImpl descriptor() {
		return Jenkins.getInstance().getDescriptorByType(
				AssetNotifier.DescriptorImpl.class);
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Perforce: Publish assets";
		}

		/**
		 * Credentials list, a Jelly config method for a build job.
		 * 
		 * @return A list of Perforce credential items to populate the jelly
		 *         Select list.
		 */
		public ListBoxModel doFillCredentialItems() {
			ListBoxModel list = new ListBoxModel();

			Class<P4StandardCredentials> type = P4StandardCredentials.class;
			Jenkins scope = Jenkins.getInstance();
			Authentication acl = ACL.SYSTEM;
			DomainRequirement domain = new DomainRequirement();

			List<P4StandardCredentials> credentials;
			credentials = CredentialsProvider.lookupCredentials(type, scope,
					acl, domain);

			if (credentials.isEmpty()) {
				list.add("Select credential...", null);
			}
			for (P4StandardCredentials c : credentials) {
				StringBuffer sb = new StringBuffer();
				sb.append(c.getDescription());
				sb.append(" (");
				sb.append(c.getUsername());
				sb.append(":");
				sb.append(c.getP4port());
				sb.append(")");
				list.add(sb.toString(), c.getId());
			}
			return list;
		}
	}
}
