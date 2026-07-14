package org.jenkinsci.plugins.p4.client;

import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4JENKINS-184: a force-clean populate that bypasses the have list ({@code -p},
 * have=false) must write every synced file to disk rather than leaving an empty
 * workspace while reporting files 'added'.
 *
 * <p>({@code p4 sync} rejects {@code -f} and {@code -p} together, so this path
 * uses {@code -p} alone — the flag translation is unit-tested in
 * {@link SyncOptionsForceBypassTest}.) The harness runs a single p4d (not a
 * replica), so this asserts the on-disk outcome directly across two sync cycles:
 * after the initial populate and again after wiping the workspace and re-running
 * the same populate, every submitted file must be present. The second cycle
 * proves {@code -p} actually re-transfers archive content — because it never
 * updates the have list — rather than passing by starting from a clean workspace
 * (AC-1, AC-4).
 */
@WithJenkins
@Issue("P4JENKINS-184")
class ForcePopulateBypassTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ForcePopulateBypassTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testForceCleanWithServerBypassWritesAllFiles() throws Exception {
		String base = "//depot/force";
		int fileCount = 12;

		for (int i = 0; i < fileCount; i++) {
			submitFile(jenkins, base + "/file" + i + ".txt", "content " + i);
		}

		// ForceCleanImpl(have=false) -> sync -p: bypass ("Populate have list"
		// unchecked). The workspace must be fully populated on disk, not left empty.
		FreeStyleProject project = createProject("force-bypass", base + "/...", new ForceCleanImpl(false, false, null, null));

		// Cycle 1: initial populate into an empty client.
		FreeStyleBuild first = build(project);
		assertAllPresent(first.getWorkspace(), fileCount);

		// Cycle 2: wipe the workspace on disk and re-run the SAME populate against the
		// SAME client. -p never updates the have list, so the server still treats the
		// client as holding nothing and re-transfers every file; this proves the sync
		// actually moves archive content rather than passing because the first run
		// happened to start from a clean workspace (P4JENKINS-184 AC-1/AC-4).
		FilePath workspace = first.getWorkspace();
		for (int i = 0; i < fileCount; i++) {
			workspace.child("file" + i + ".txt").delete();
		}

		FreeStyleBuild second = build(project);
		assertAllPresent(second.getWorkspace(), fileCount);
	}

	/** Assert every file0..N-1 exists on disk and the count matches exactly. */
	private void assertAllPresent(FilePath workspace, int fileCount) throws Exception {
		int onDisk = 0;
		for (int i = 0; i < fileCount; i++) {
			FilePath file = workspace.child("file" + i + ".txt");
			assertTrue(file.exists(), "file" + i + ".txt reported synced but missing on disk");
			onDisk++;
		}
		assertEquals(fileCount, onDisk, "on-disk file count must match the submitted file count");
	}

	/** Freestyle job syncing a whole depot view into a single fixed client. */
	private FreeStyleProject createProject(String jobName, String depotView, Populate populate) throws Exception {
		String client = jobName + ".ws";
		String view = depotView + " //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		workspace.setExpand(new HashMap<>());

		FreeStyleProject project = jenkins.createFreeStyleProject(jobName);
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();
		return project;
	}

	private FreeStyleBuild build(FreeStyleProject project) throws Exception {
		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult(), "force-bypass sync build should succeed");
		return build;
	}
}
