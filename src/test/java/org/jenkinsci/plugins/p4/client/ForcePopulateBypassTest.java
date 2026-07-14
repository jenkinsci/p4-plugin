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
 * P4JENKINS-184: a force populate that also uses {@code -p} (serverBypass,
 * have=false) must still transfer archive content, so the workspace is populated
 * rather than left empty when the server believes files are 'already synced'.
 *
 * <p>The regression coupled {@code -f} to the have flag, so a {@code ForceCleanImpl}
 * with have=false silently dropped {@code -f}; against a read-only replica this left
 * an empty workspace while reporting files 'added'. The harness runs a single p4d
 * (not a replica), so this asserts the on-disk outcome directly: every submitted
 * file must be present after a force+bypass sync (AC-1, AC-4).
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

		// ForceCleanImpl(have=false) -> sync -p -f: bypass the have-table but still
		// force archive transfer. Before the fix -f was dropped and the workspace
		// could come back empty.
		FreeStyleBuild build = buildDirSync("force-bypass", base + "/...", new ForceCleanImpl(false, false, null, null));

		FilePath workspace = build.getWorkspace();
		int onDisk = 0;
		for (int i = 0; i < fileCount; i++) {
			FilePath file = workspace.child("file" + i + ".txt");
			assertTrue(file.exists(), "file" + i + ".txt reported synced but missing on disk");
			onDisk++;
		}
		assertEquals(fileCount, onDisk, "on-disk file count must match the submitted file count");
	}

	/** Build a freestyle job syncing a whole depot view and return the completed build. */
	private FreeStyleBuild buildDirSync(String jobName, String depotView, Populate populate) throws Exception {
		String client = jobName + ".ws";
		String view = depotView + " //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		workspace.setExpand(new HashMap<>());

		FreeStyleProject project = jenkins.createFreeStyleProject(jobName);
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult(), "force-bypass sync build should succeed");
		return build;
	}
}
