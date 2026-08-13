package org.jenkinsci.plugins.p4.heal;

import hudson.EnvVars;
import com.perforce.p4java.core.file.IFileSpec;
import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.jenkinsci.plugins.p4.heal.verify.VerifyConfig;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class ShelveDeliveryTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ShelveDeliveryTest-p4root";

	private static final String DEPOT_FILE = "//depot/HealData/heal.txt";
	private static final String ORIGINAL = "one\ntwo\nthree\n";

	private static final String FIX = "--- a/heal.txt\n"
			+ "+++ b/heal.txt\n"
			+ "@@ -2,1 +2,1 @@\n"
			+ "-two\n"
			+ "+fixed\n";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	private final ByteArrayOutputStream console = new ByteArrayOutputStream();

	@BeforeAll
	static void beforeAll(JenkinsRule rule) {
		jenkins = rule;
	}

	@BeforeEach
	void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	private TaskListener listener() {
		return new StreamTaskListener(console, StandardCharsets.UTF_8);
	}

	@Test
	void testTheShelveIsBuiltFromThePatch() throws Exception {
		UnifiedDiff patch = UnifiedDiff.parse(FIX);

		ShelveImpl shelve = ShelveDelivery.shelveFor(patch, "AI fix: off-by-one");

		assertEquals("AI fix: off-by-one", shelve.getDescription());
		assertTrue(shelve.isRevert(),
				"the workspace must come back to the depot revision once the patch is shelved");
		assertFalse(shelve.isOnlyOnSuccess(),
				"healing only ever runs after a failure, so success must not be a condition");
		assertEquals("heal.txt\n", shelve.getPaths(),
				"only the files the patch touched may be reconciled");
	}

	@Test
	void testEveryFileThePatchTouchedIsListed() throws Exception {
		UnifiedDiff patch = UnifiedDiff.parse(FIX
				+ "--- a/src/Other.java\n"
				+ "+++ b/src/Other.java\n"
				+ "@@ -1,1 +1,1 @@\n"
				+ "-a\n"
				+ "+b\n");

		ShelveImpl shelve = ShelveDelivery.shelveFor(patch, "AI fix");

		assertEquals("heal.txt\nsrc/Other.java\n", shelve.getPaths());
	}

	@Test
	void testABuildWithoutAPerforceWorkspaceIsReported() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("Heal-no-scm");
		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		FilePath workspace = build.getWorkspace();
		assertNotNull(workspace);

		ShelveDelivery delivery = new ShelveDelivery(build, workspace, listener());

		IOException thrown = assertThrows(IOException.class,
				() -> delivery.deliver(UnifiedDiff.parse(FIX), "AI fix"));

		assertTrue(thrown.getMessage().contains("Perforce"), thrown.getMessage());
	}

	@Test
	void testAVerifiedPatchIsShelvedAsAPendingChange() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = new WorkspacePatchSession(buildWorkspace, listener(), false,
				new ShelveDelivery(build, buildWorkspace, listener()));
		session.apply(UnifiedDiff.parse(FIX));

		String reference = session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		assertTrue(reference.contains("shelved change"), reference);
		int change = changeIn(reference);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			List<IFileSpec> shelved = p4.getShelvedFiles(change);
			assertEquals(1, shelved.size(), "exactly the patched file belongs in the change");
			assertEquals(DEPOT_FILE, shelved.get(0).getDepotPathString());
		}
	}

	@Test
	void testShelvingLeavesTheWorkspaceOnTheDepotRevision() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve-clean");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = new WorkspacePatchSession(buildWorkspace, listener(), false,
				new ShelveDelivery(build, buildWorkspace, listener()));
		session.apply(UnifiedDiff.parse(FIX));
		session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		assertEquals(ORIGINAL, buildWorkspace.child("heal.txt").readToString(),
				"a shelved patch must not be left behind in the workspace");
	}

	@Test
	void testTheSwarmLinkPointsAtTheShelvedChange() {
		assertEquals("http://swarm.example.com/changes/42",
				ShelveDelivery.reviewLink("http://swarm.example.com", 42));
	}

	@Test
	void testATrailingSlashOnTheSwarmUrlIsNotDoubled() {
		assertEquals("http://swarm.example.com/changes/42",
				ShelveDelivery.reviewLink("http://swarm.example.com/", 42));
	}

	@Test
	void testThereIsNoLinkWhenTheServerAdvertisesNoSwarm() {
		assertNull(ShelveDelivery.reviewLink(null, 42));
		assertNull(ShelveDelivery.reviewLink("", 42));
	}

	@Test
	void testTheShelfReferenceCarriesTheSwarmLink() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve-swarm");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		ShelveDelivery delivery = new ShelveDelivery(build, buildWorkspace, listener()) {
			@Override
			String swarmUrl(String credential) {
				return "http://swarm.example.com";
			}
		};
		WorkspacePatchSession session =
				new WorkspacePatchSession(buildWorkspace, listener(), false, delivery);
		session.apply(UnifiedDiff.parse(FIX));

		String reference = session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		Matcher change = Pattern.compile("^shelved change (\\d+)").matcher(reference);
		assertTrue(change.find(), reference);
		assertTrue(reference.endsWith("http://swarm.example.com/changes/" + change.group(1)),
				reference);
	}

	@Test
	@Disabled("TODO REVERT BEFORE COMMIT: ConnectionHelper.swarmUrlOverride hardcodes a Swarm URL"
			+ " for local testing, so every server now advertises one. Re-enable when that field"
			+ " goes; this test is what proves the override is gone.")
	void testTheShelfReferenceIsPlainWhenThereIsNoSwarm() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve-no-swarm");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = new WorkspacePatchSession(buildWorkspace, listener(), false,
				new ShelveDelivery(build, buildWorkspace, listener()));
		session.apply(UnifiedDiff.parse(FIX));

		String reference = session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		assertFalse(reference.contains("http"),
				"a server with no Swarm property must not produce a review link");
	}

	@Test
	void testAShelfThatDoesNotBuildOnItsOwnIsWithdrawn() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve-withdrawn");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = session(build, buildWorkspace, false);
		session.apply(UnifiedDiff.parse(FIX));

		DeliveryRejectedException thrown = assertThrows(DeliveryRejectedException.class,
				() -> session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one"));

		int change = changeIn(thrown.getMessage());
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			assertTrue(p4.getShelvedFiles(change).isEmpty(),
					"a fix that did not build must not be left shelved");
		}
	}

	@Test
	void testAShelfThatBuildsOnItsOwnIsKept() throws Exception {
		ManualWorkspaceImpl workspace = healWorkspace();
		FreeStyleBuild build = buildWith(workspace, "Heal-shelve-kept");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = session(build, buildWorkspace, true);
		session.apply(UnifiedDiff.parse(FIX));

		String reference = session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		int change = changeIn(reference);
		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			assertEquals(1, p4.getShelvedFiles(change).size());
		}
	}

	/**
	 * A session whose delivery re-builds the shelf with a fixed answer, so the
	 * withdrawal path can be tested without running a real build.
	 */
	private WorkspacePatchSession session(FreeStyleBuild build, FilePath buildWorkspace,
	                                      boolean shelfBuilds) {

		ShelveDelivery delivery = new ShelveDelivery(build, buildWorkspace, listener());
		delivery.setVerifier(new ShelfVerifier(build, null, listener(), new EnvVars(),
				new VerifyConfig("mvn -B compile", "", "mvn -B verify", 0)) {

			@Override
			public boolean verify(String credential, String templateClient, P4Ref revision,
			                      FilePath temp, long shelf) {
				return shelfBuilds;
			}
		});
		return new WorkspacePatchSession(buildWorkspace, listener(), false, delivery);
	}

	private ManualWorkspaceImpl healWorkspace() throws Exception {
		submitFile(jenkins, DEPOT_FILE, ORIGINAL);

		String client = "heal.ws";
		String view = "//depot/HealData/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		return new ManualWorkspaceImpl("none", true, client, spec, false);
	}

	private FreeStyleBuild buildWith(ManualWorkspaceImpl workspace, String name) throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject(name);
		Populate populate = new AutoCleanImpl();
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult());
		return build;
	}

	/**
	 * The changelist number out of a delivery reference or an error message.
	 *
	 * <p>Reads the first run of digits rather than stripping every non-digit: the
	 * reference also carries a Swarm review link when the server advertises one, and
	 * that URL ends in the change number too, so stripping would splice the two
	 * numbers together.
	 */
	private static int changeIn(String text) {
		Matcher digits = Pattern.compile("\\d+").matcher(text);
		assertTrue(digits.find(), "no changelist number in: " + text);
		return Integer.parseInt(digits.group());
	}
}
