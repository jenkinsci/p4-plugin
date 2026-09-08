package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.option.client.AddFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.PublishNotifier;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4Publish (shelve) with 'paths' set shelves every listed file that Perforce OPENS - shelve operates on
 * opened files, not on "content changed" files. Reconcile's filetype detection ('-t', used on 2019.1+
 * servers) opens a file whose depot type differs from its detected type even when the content is unchanged
 * (e.g. a binary+l file holding text), so it also gets shelved. The "Detect filetype changes" option
 * controls whether '-t' is passed: ticked (default) shelves the unchanged listed file too; unticked runs
 * reconcile without '-t' so only genuinely edited files are shelved.
 */
@WithJenkins
@Issue("P4JENKINS-183")
class PublishPathsTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-PublishPathsTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testShelvePathsShelvesAllOpenedListedFiles() throws Exception {
		String client = "paths-bug.ws";
		String root = "target/paths-bug.ws";
		String view = "//depot/pathsbug/... //" + client + "/...";

		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false,
				null, "LOCAL", view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		workspace.setExpand(new HashMap<>());
		File wsRoot = new File(root).getAbsoluteFile();
		wsRoot.mkdirs();
		workspace.setRootPath(wsRoot.toString());

		String depot1 = "//depot/pathsbug/1.bin";
		String depot4 = "//depot/pathsbug/4.bin";
		String clientPath1 = "//" + client + "/1.bin";
		String clientPath4 = "//" + client + "/4.bin";

		// Submit two binary+l files, matching P4JENKINS-183's reproduction exactly.
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();

			Files.write(new File(wsRoot, "1.bin").toPath(), "File1\n".getBytes(StandardCharsets.UTF_8));
			Files.write(new File(wsRoot, "4.bin").toPath(), "File4\n".getBytes(StandardCharsets.UTF_8));

			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath1, clientPath4);
			iclient.addFiles(files, new AddFilesOptions());

			ReopenFilesOptions retype = new ReopenFilesOptions();
			retype.setFileType("binary+l");
			iclient.reopenFiles(files, retype);

			Changelist change = new Changelist();
			change.setDescription("binary+l files");
			change = (Changelist) iclient.createChangelist(change);
			ReopenFilesOptions toChange = new ReopenFilesOptions();
			toChange.setChangelistId(change.getId());
			iclient.reopenFiles(files, toChange);
			change.refresh();
			change.submit(false);
		}

		// Real Jenkins job, matching the ticket's Jenkinsfile: checkout (sync both files),
		// edit ONLY 4.bin, then p4publish shelve listing BOTH files in 'paths'.
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-Paths-Bug");

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Equivalent of the ticket's "chmod -R +w bin; echo test >> 4.bin" - only 4.bin changes.
		project.getBuildersList().add(new CreateArtifact("4.bin", "File4\nedited by build\n"));

		ShelveImpl publish = new ShelveImpl("Shelved by Jenkins. Build: ${BUILD_TAG}", false, false, false, false);
		publish.setPaths(depot1 + "\n" + depot4);
		PublishNotifier notifier = new PublishNotifier(CREDENTIAL, workspace, publish);
		project.getPublishersList().add(notifier);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Find the changelist the build just shelved into, and check what actually landed in it.
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> allFiles = FileSpecBuilder.makeFileSpecList("//" + iclient.getName() + "/...");
			GetChangelistsOptions clOpts = new GetChangelistsOptions();
			clOpts.setType(IChangelist.Type.PENDING);
			List<IChangelistSummary> pending = p4.getConnection().getChangelists(allFiles, clOpts);

			List<IFileSpec> shelved = null;
			for (IChangelistSummary cl : pending) {
				if (cl.getDescription() != null && cl.getDescription().startsWith("Shelved by Jenkins")) {
					shelved = p4.getConnection().getShelvedFiles(cl.getId());
					break;
				}
			}
			assertTrue(shelved != null, "should find the changelist the build shelved into");

			List<String> shelvedPaths = shelved.stream()
					.map(IFileSpec::getDepotPathString)
					.collect(Collectors.toList());

			assertTrue(shelvedPaths.contains(depot4), "the genuinely edited file must be shelved");
			// 1.bin's content is unchanged, but it is stored as binary+l while reconcile -t detects it as
			// text+l, so reconcile OPENS it for a filetype change - and p4publish shelves opened files.
			// Shelving it too is therefore the expected behaviour (verified: no plugin code change needed).
			assertTrue(shelvedPaths.contains(depot1),
					"P4JENKINS-183 (works as designed): a listed file opened by reconcile is shelved; shelved=" + shelvedPaths);
		}
	}

	@Test
	void testShelvePathsWithoutFileTypeShelvesOnlyEditedFile() throws Exception {
		String client = "filetypeoff.ws";
		String root = "target/filetypeoff.ws";
		String view = "//depot/filetypeoff/... //" + client + "/...";

		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false,
				null, "LOCAL", view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		workspace.setExpand(new HashMap<>());
		File wsRoot = new File(root).getAbsoluteFile();
		wsRoot.mkdirs();
		workspace.setRootPath(wsRoot.toString());

		String depot1 = "//depot/filetypeoff/1.bin";
		String depot4 = "//depot/filetypeoff/4.bin";
		String clientPath1 = "//" + client + "/1.bin";
		String clientPath4 = "//" + client + "/4.bin";

		// Submit two binary+l files holding plain text (a file stored binary but containing text).
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();

			Files.write(new File(wsRoot, "1.bin").toPath(), "File1\n".getBytes(StandardCharsets.UTF_8));
			Files.write(new File(wsRoot, "4.bin").toPath(), "File4\n".getBytes(StandardCharsets.UTF_8));

			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath1, clientPath4);
			iclient.addFiles(files, new AddFilesOptions());

			ReopenFilesOptions retype = new ReopenFilesOptions();
			retype.setFileType("binary+l");
			iclient.reopenFiles(files, retype);

			Changelist change = new Changelist();
			change.setDescription("binary+l files");
			change = (Changelist) iclient.createChangelist(change);
			ReopenFilesOptions toChange = new ReopenFilesOptions();
			toChange.setChangelistId(change.getId());
			iclient.reopenFiles(files, toChange);
			change.refresh();
			change.submit(false);
		}

		// Real Jenkins job: sync both files, edit ONLY 4.bin, then p4publish shelve listing BOTH files in
		// 'paths' with "Detect filetype changes" UNticked.
		FreeStyleProject project = jenkins.createFreeStyleProject("Publish-FileType-Off");

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		// Only 4.bin is genuinely modified.
		project.getBuildersList().add(new CreateArtifact("4.bin", "File4\nedited by build\n"));

		ShelveImpl publish = new ShelveImpl("Shelved by Jenkins. Build: ${BUILD_TAG}", false, false, false, false);
		publish.setPaths(depot1 + "\n" + depot4);
		// Untick "Detect filetype changes" - reconcile runs without -t.
		publish.setFileType(false);
		PublishNotifier notifier = new PublishNotifier(CREDENTIAL, workspace, publish);
		project.getPublishersList().add(notifier);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Only the edited 4.bin should be shelved; the unchanged 1.bin must be excluded now that -t is off.
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> allFiles = FileSpecBuilder.makeFileSpecList("//" + iclient.getName() + "/...");
			GetChangelistsOptions clOpts = new GetChangelistsOptions();
			clOpts.setType(IChangelist.Type.PENDING);
			List<IChangelistSummary> pending = p4.getConnection().getChangelists(allFiles, clOpts);

			List<IFileSpec> shelved = null;
			for (IChangelistSummary cl : pending) {
				if (cl.getDescription() != null && cl.getDescription().startsWith("Shelved by Jenkins")) {
					shelved = p4.getConnection().getShelvedFiles(cl.getId());
					break;
				}
			}
			assertTrue(shelved != null, "should find the changelist the build shelved into");

			List<String> shelvedPaths = shelved.stream()
					.map(IFileSpec::getDepotPathString)
					.collect(Collectors.toList());

			assertTrue(shelvedPaths.contains(depot4),
					"the genuinely edited file must be shelved; shelved=" + shelvedPaths);
			assertFalse(shelvedPaths.contains(depot1),
					"with 'Detect filetype changes' off, unchanged 1.bin must NOT be shelved; shelved=" + shelvedPaths);
		}
	}
}
