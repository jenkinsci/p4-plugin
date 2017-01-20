package org.jenkinsci.plugins.p4;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

abstract public class DefaultEnvironment {

	protected final static String VERSION = "r15.1";
	protected final static String CREDENTIAL = "id";
	protected final static int HTTP_PORT = 1888;
	protected final static String HTTP_URL = "http://localhost:" + HTTP_PORT;
	protected final int LOG_LIMIT = 1000;

	protected P4PasswordImpl createCredentials(String user, String password, SampleServerRule p4d) throws IOException {
		String p4port = p4d.getRshPort();
		CredentialsScope scope = CredentialsScope.SYSTEM;
		P4PasswordImpl auth = new P4PasswordImpl(scope, CREDENTIAL, "desc", p4port, null, user, "0", "0", null, password);
		SystemCredentialsProvider.getInstance().getCredentials().clear();
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();
		return auth;
	}

	protected static void startHttpServer(int port) throws Exception {
		DummyServer server = new DummyServer(port);
		new Thread(server).start();
	}

	protected String defaultClient() {
		String client = "test.ws";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			client = "test.win";
		}
		return client;
	}

	protected static final class CreateArtifact extends Builder {
		private final String filename;
		private final String content;

		public CreateArtifact(String filename, String content) {
			this.filename = filename;
			this.content = content;
		}

		@Override
		public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
			build.getWorkspace().child(filename).write(content, "UTF-8");
			return true;
		}
	}

	protected void submitFile(JenkinsRule jenkins, String path, String content) throws Exception {
		String filename = path.substring(path.lastIndexOf("/") + 1, path.length());

		// Create workspace
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = path + " //" + client + "/" + filename;
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// Populate with P4 scm
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// Freestyle job
		FreeStyleProject project = jenkins.createFreeStyleProject();
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact(filename, content));

		// Submit artifacts
		SubmitImpl submit = new SubmitImpl("publish", true, true, true, "3");
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, submit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}
}
