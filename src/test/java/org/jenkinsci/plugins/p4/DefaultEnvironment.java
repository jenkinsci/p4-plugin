package org.jenkinsci.plugins.p4;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.CommitImpl;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;

abstract public class DefaultEnvironment {

	protected final static String R15_1 = "r15.1";
	protected final static String R17_1 = "r17.1";

	protected final static String CREDENTIAL = "id";
	protected final static int HTTP_PORT = 1888;
	protected final static String HTTP_URL = "http://localhost:" + HTTP_PORT;
	protected final int LOG_LIMIT = 1000;

	protected P4PasswordImpl createCredentials(String user, String password, String p4port, String id) throws IOException {
		CredentialsScope scope = CredentialsScope.GLOBAL;
		P4PasswordImpl auth = new P4PasswordImpl(scope, id, "desc", p4port, null, user, "0", "0", null, password);
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
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view, null, null, null, true);
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

	protected void commitFile(JenkinsRule jenkins, String path, String content) throws Exception {
		String filename = path.substring(path.lastIndexOf("/") + 1, path.length());

		// Create workspace
		String client = "graphCommit.ws";
		String stream = null;
		String line = "LOCAL";
		String view = path + " //" + client + "/" + filename;
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view, null, "graph", null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// Populate with P4 scm
		Populate populate = new GraphHybridImpl(false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// Freestyle job
		FreeStyleProject project = jenkins.createFreeStyleProject();
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact(filename, content));

		// Submit artifacts
		CommitImpl commit = new CommitImpl("publish", true, true);
		commit.addFile(path);
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, commit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}

	public class TestHandler extends Handler {

		private StringBuffer sb = new StringBuffer();

		@Override
		public void publish(LogRecord record) {
			sb.append(record.getMessage());
			sb.append("\n");
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}

		public String getLogBuffer() {
			return sb.toString();
		}
	}
}
