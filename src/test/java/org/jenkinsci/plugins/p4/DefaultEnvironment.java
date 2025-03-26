package org.jenkinsci.plugins.p4;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IOptionsServer;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.CommitImpl;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

abstract public class DefaultEnvironment {

	private static Logger logger = Logger.getLogger(DefaultEnvironment.class.getName());

	protected final static String R15_1 = "r15.1";
	protected final static String R17_1 = "r17.1";
	protected final static String R18_1 = "r18.1";

	protected final static String R19_1 = "r19.1";

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

	protected Workspace defaultWorkspace(String name) {
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, name);
		return workspace;
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

	protected String shelveFile(JenkinsRule jenkins, String path, String content) throws Exception {
		ManualWorkspaceImpl workspace = createWorkspace(path);
		FilePath filePath = createFilePath(path, content, workspace);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			Publish publish = new ShelveImpl("Submit test files", false, false, false, false);
			boolean open = p4.buildChange(publish);
			if (open) {
				return p4.publishChange(publish);
			}
		} finally {
			filePath.delete();
		}
		return null;
	}

	protected String submitFile(JenkinsRule jenkins, String path, String content) throws Exception {
		return submitFile(jenkins, path, content, "Submit test files");
	}

	protected String submitFile(JenkinsRule jenkins, String path, String content, String desc) throws Exception {
		ManualWorkspaceImpl workspace = createWorkspace(path);
		return submitFile(jenkins, path, content, desc, workspace);
	}

	protected String submitStreamFile(JenkinsRule jenkins, String path, String content, String desc) throws Exception {
		StreamWorkspaceImpl workspace = createStreamsWorkspace(path, 2);
		return submitFile(jenkins, path, content, desc, workspace);
	}

	protected String submitFile(JenkinsRule jenkins, String path, String content, String desc, Workspace workspace) throws Exception {
		FilePath filePath = createFilePath(path, content, workspace);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			setP4Ignore(p4, "other");
			Publish publish = new SubmitImpl(desc, false, false, false, false, null);
			boolean open = p4.buildChange(publish);
			if (open) {
				return p4.publishChange(publish);
			}
		} finally {
			filePath.delete();
		}
		return null;
	}

	private void setP4Ignore(ConnectionHelper p4, String p4ignore) {
		IOptionsServer connection = p4.getConnection();
		Server server = (Server) connection;
		server.setIgnoreFileName(p4ignore);
	}

	private ManualWorkspaceImpl createWorkspace(String path) {
		String filename = path.substring(path.lastIndexOf("/") + 1);

		// Create workspace
		String client = "submit.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "\"" + path + "\"" + " //" + client + "/" + filename;
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		workspace.setExpand(new HashMap<>());

		File wsRoot = new File("target/submit.ws").getAbsoluteFile();
		workspace.setRootPath(wsRoot.toString());

		return workspace;
	}

	private StreamWorkspaceImpl createStreamsWorkspace(String path, int depth) {
		String[] p = path.substring(2).split("/");

		StringBuilder sb = new StringBuilder("//");
		for (int i = 0; i < depth; i++) {
			sb.append(p[i]);
			sb.append("/");
		}

		String stream = sb.substring(0, sb.lastIndexOf("/"));

		String client = "stream.ws";
		StreamWorkspaceImpl workspace = new StreamWorkspaceImpl("none", false, stream, client);
		workspace.setExpand(new HashMap<>());

		File wsRoot = new File("target/stream.ws").getAbsoluteFile();
		workspace.setRootPath(wsRoot.toString());

		return workspace;
	}

	private FilePath createFilePath(String path, String content, Workspace workspace) throws IOException, InterruptedException {
		String filename = path.substring(path.lastIndexOf("/") + 1);

		File wsRoot = new File(workspace.getRootPath()).getAbsoluteFile();

		File file = new File(wsRoot + File.separator + filename).getAbsoluteFile();
		FilePath filePath = new FilePath(file);
		filePath.delete();
		filePath.write(content, "UTF-8");

		return filePath;
	}

	protected void commitFile(JenkinsRule jenkins, String path, String content) throws Exception {
		String filename = path.substring(path.lastIndexOf("/") + 1);

		// Create workspace
		String client = "graphCommit.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "\"" + path + "\"" + " //" + client + "/" + filename;
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false, stream, line, view, null, "graph", null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// Populate with P4 scm
		Populate populate = new GraphHybridImpl(false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// Freestyle job
		FreeStyleProject project = jenkins.createFreeStyleProject();
		project.setScm(scm);

		// Create artifact files
		project.getBuildersList().add(new CreateArtifact(filename, content));

		// Submit artifacts
		CommitImpl commit = new CommitImpl("publish", true, true, false);
		commit.addFile(path);
		PublishNotifier publish = new PublishNotifier(CREDENTIAL, workspace, commit);
		project.getPublishersList().add(publish);
		project.save();

		// Start build
		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}

	protected boolean waitForBuild(WorkflowJob job, int buildNumber) throws InterruptedException {
		return waitForBuild(job, buildNumber, 170, 200L);
	}

	protected boolean waitForBuild(WorkflowJob job, int buildNumber, final int retry, long delay) throws InterruptedException {
		int r = retry;
		while (r > 0) {
			r--;
			if (job.getLastBuild().number == buildNumber) {
				logger.info("waitForBuild(): Attempts: " + (retry - r));
				return true;
			}
			Thread.sleep(delay);
		}
		logger.severe("Gave up waiting for build");
		return false;
	}

	public static class TestHandler extends Handler {

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

		public void clear() {
			sb = new StringBuffer();
		}
	}
}