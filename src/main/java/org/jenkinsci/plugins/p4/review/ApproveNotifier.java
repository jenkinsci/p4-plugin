package org.jenkinsci.plugins.p4.review;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributor;
import hudson.model.Item;
import hudson.model.Node;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.logging.Logger;

public class ApproveNotifier extends Notifier {

	private static Logger logger = Logger.getLogger(ApproveNotifier.class.getName());

	private final String credential;
	private final String review;
	private final String status;
	private String description;

	public String getCredential() {
		return credential;
	}

	public String getReview() {
		return review;
	}

	public String getStatus() {
		return status;
	}

	public String getDescription() {
		return description;
	}

	@DataBoundConstructor
	public ApproveNotifier(String credential, String review, String status) {
		this.credential = credential;
		this.review = review;
		this.status = status;
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {

		AbstractProject<?, ?> job = build.getParent();
		Node node = build.getBuiltOn();
		EnvVars env = job.getEnvironment(node, listener);

		for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView()) {
			ec.buildEnvironmentFor(build, env, listener);
		}

		ConnectionHelper p4 = new ConnectionHelper(job, getCredential(), listener);

		try {
			return approveReview(p4, env);
		} catch (Exception e) {
			throw new InterruptedException("Unable to update Review.");
		}
	}

	protected boolean approveReview(ConnectionHelper p4, EnvVars env) throws Exception {
		SwarmHelper swarm = new SwarmHelper(p4, "11");

		ApproveState state = ApproveState.parse(getStatus());
		if (state == null) {
			p4.log("Unknown Swarm review state: " + getStatus());
			return false;
		}

		// Expand review ID using environment
		Expand expand = new Expand(env);
		String rev = getReview();
		rev = expand.format(rev, false);

		// If defined, expand description and add parameter
		String desc = getDescription();
		if(desc != null && !desc.isEmpty()) {
			desc = expand.format(desc, false);
		}

		return swarm.approveReview(rev, state, desc);
	}

	public static DescriptorImpl descriptor() {
		Jenkins j = Jenkins.get();
		if (j != null) {
			j.getDescriptorByType(ApproveNotifier.DescriptorImpl.class);
		}
		return null;
	}

	@Extension
	@Symbol("approve")
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce: ApproveImpl review";
		}

		public ListBoxModel doFillCredentialItems(@AncestorInPath Item project, @QueryParameter String credential) {
			return P4CredentialsImpl.doFillCredentialItems(project, credential);
		}

		public static ListBoxModel doFillStatusItems() {
			ListBoxModel listBoxModel = new ListBoxModel();
			for (ApproveState s : ApproveState.values()) {
				listBoxModel.add(s.getDescription(), s.name());
			}
			return listBoxModel;
		}
	}
}
