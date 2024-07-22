package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.LogTaskListener;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class P4SCMFileSystem extends SCMFileSystem {

	private static Logger logger = Logger.getLogger(P4SCMFileSystem.class.getName());

	private TempClientHelper p4;

	private WorkflowJob _job = null;

	private String credential;

	protected P4SCMFileSystem(@NonNull Item owner, @NonNull PerforceScm scm, @CheckForNull P4SCMRevision rev) throws Exception {
		super(rev);
		credential = scm.getCredential();
		Workspace ws = scm.getWorkspace().deepClone();

		// Set environment in Workspace
		if (owner instanceof WorkflowJob) {
			_job = (WorkflowJob) owner;
			Run<?,?> build = _job.getLastBuild();
			EnvVars env = build.getEnvironment(new LogTaskListener(logger, Level.INFO));
			ws.setExpand(env);
		}

		this.p4 = new TempClientHelper(owner, credential, null, ws);
	}

	public void addJenkinsFilePath(String path) {
		if (_job == null || _job.getLastBuild() == null) {
			return;
		}
		CpsScmFlowDefinition definition = (CpsScmFlowDefinition) _job.getDefinition();
		if (definition == null) {
			return;
		}
		if (StringUtils.isNotEmpty(definition.getScriptPath()) && definition.isLightweight()) {
			try {
				Run<?, ?> build = _job.getLastBuild();
				TagAction tag = new TagAction(build, credential);
				tag.setJenkinsPath(path);
				build.addAction(tag);
			} catch (IOException | InterruptedException e) {
				logger.warning("P4: Failed to create temporary TagAction for JENKINSFILE_PATH.");
			}
		}
	}

	@Override
	public void close() throws IOException {
		p4.close();
	}

	@Override
	public long lastModified() throws IOException, InterruptedException {
		return 0;
	}

	@Override
	public SCMFile getRoot() {
		return new P4SCMFile(this);
	}

	@Extension
	public static class BuilderImpl extends SCMFileSystem.Builder {

		@Override
		public boolean supports(SCM source) {
			if (source instanceof PerforceScm) {
				return true;
			}
			return false;
		}

		@Override
		public boolean supports(SCMSource source) {
			if (source instanceof AbstractP4ScmSource) {
				return true;
			}
			return false;
		}

		@Override
		protected boolean supportsDescriptor(SCMDescriptor scmDescriptor) {
			return false;
		}

		@Override
		protected boolean supportsDescriptor(SCMSourceDescriptor scmSourceDescriptor) {
			return false;
		}

		@Override
		public SCMFileSystem build(@NonNull Item owner, SCM scm, @CheckForNull SCMRevision rev) throws IOException, InterruptedException {
			if (scm == null || !(scm instanceof PerforceScm)) {
				return null;
			}
			PerforceScm p4scm = (PerforceScm) scm;

			if (rev != null && !(rev instanceof P4SCMRevision)) {
				return null;
			}
			P4SCMRevision p4rev = (P4SCMRevision) rev;

			try {
				return new P4SCMFileSystem(owner, p4scm, p4rev);
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
	}

	public TempClientHelper getConnection() {
		return p4;
	}
}
