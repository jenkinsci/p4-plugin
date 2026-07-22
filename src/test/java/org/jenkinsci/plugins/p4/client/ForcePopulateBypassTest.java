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
import org.jenkinsci.plugins.p4.populate.ParallelSync;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
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
 * P4JENKINS-184: a populate that bypasses the have list ({@code -p}, have=false)
 * while parallel sync is enabled must still write every synced file to disk rather
 * than leaving an empty workspace while the console reports files as synced.
 *
 * <p>Root cause is a p4java defect: a parallel sync combined with {@code -p}
 * (ServerBypass) does not honor the server's {@code doPublish} directive on a
 * read-only / forwarding replica, so the transmit workers deliver 0 files. The fix
 * ({@link ClientHelper#useParallelSync}) disables parallel transfer whenever the
 * have list is bypassed, independent of force, falling back to a serial {@code -p}
 * sync which transfers content correctly. The flag/gate decisions are unit-tested
 * in {@link SyncOptionsForceBypassTest}.
 *
 * <p>This end-to-end test exercises the exact trigger condition (parallel enabled +
 * bypass) and asserts the on-disk outcome across two sync cycles: after the initial
 * populate, and again after wiping the workspace and re-running the same populate
 * against the same client, every submitted file must be present (AC-1, AC-4).
 *
 * <p>Note: the harness runs a single {@code rsh:}-mode p4d, which cannot reproduce
 * the replica-specific transmit-worker failure itself (that needs a TCP master plus
 * a {@code forwarding-replica} target — infrastructure this repo's test harness does
 * not provide). What this test does verify deterministically is that a parallel +
 * bypass populate now takes the safe serial path and leaves a fully populated
 * workspace; the trigger-condition logic is covered by {@link SyncOptionsForceBypassTest}.
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
	void testForceCleanParallelBypassWritesAllFiles() throws Exception {
		String base = "//depot/force";
		int fileCount = 12;

		for (int i = 0; i < fileCount; i++) {
			submitFile(jenkins, base + "/file" + i + ".txt", "content " + i);
		}

		// ForceCleanImpl(have=false) + parallel: the exact P4JENKINS-184 trigger.
		// The fix disables parallel for -p syncs, so this runs a serial -p sync and
		// fully populates the workspace.
		Populate populate = new ForceCleanImpl(false, false, null, enabledParallel());
		assertPopulatesAcrossCycles("force-bypass", base, fileCount, populate);
	}

	@Test
	void testSyncOnlyParallelBypassWritesAllFiles() throws Exception {
		String base = "//depot/synconly";
		int fileCount = 12;

		for (int i = 0; i < fileCount; i++) {
			submitFile(jenkins, base + "/file" + i + ".txt", "content " + i);
		}

		// SyncOnlyImpl(force=false, have=false) + parallel: same bug condition WITHOUT
		// a force requested -- the case a force-based fix would miss.
		Populate populate = new SyncOnlyImpl(false, false, false, false, null, enabledParallel());
		assertPopulatesAcrossCycles("synconly-bypass", base, fileCount, populate);
	}

	/**
	 * Run the populate twice against the same client: fresh populate, then wipe the
	 * workspace on disk and re-run. Every file must be on disk after each cycle.
	 */
	private void assertPopulatesAcrossCycles(String jobName, String base, int fileCount, Populate populate) throws Exception {
		FreeStyleProject project = createProject(jobName, base + "/...", populate);

		FreeStyleBuild first = build(project);
		assertAllPresent(first.getWorkspace(), base, fileCount);

		FilePath workspace = first.getWorkspace();
		for (int i = 0; i < fileCount; i++) {
			workspace.child("file" + i + ".txt").delete();
		}

		FreeStyleBuild second = build(project);
		assertAllPresent(second.getWorkspace(), base, fileCount);
	}

	/** Assert every file0..N-1 exists on disk and the count matches exactly. */
	private void assertAllPresent(FilePath workspace, String base, int fileCount) throws Exception {
		int onDisk = 0;
		for (int i = 0; i < fileCount; i++) {
			FilePath file = workspace.child("file" + i + ".txt");
			assertTrue(file.exists(), base + "/file" + i + ".txt reported synced but missing on disk");
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
		assertEquals(Result.SUCCESS, build.getResult(), "parallel-bypass sync build should succeed");
		return build;
	}

	private static ParallelSync enabledParallel() {
		// threads=4, min=1, minsize=1024 -- as in the ticket's --parallel spec.
		return new ParallelSync(true, null, "4", "1", "1024");
	}
}
