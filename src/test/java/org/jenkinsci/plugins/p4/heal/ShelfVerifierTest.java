package org.jenkinsci.plugins.p4.heal;

import hudson.EnvVars;
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
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.jenkinsci.plugins.p4.heal.verify.VerifyConfig;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class ShelfVerifierTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ShelfVerifierTest-p4root";

	private static final String DEPOT_FILE = "//depot/VerifyData/heal.txt";
	private static final String ORIGINAL = "one\ntwo\nthree\n";
	private static final String PATCHED = "one\nfixed\nthree\n";

	private static final String FIX = "--- a/heal.txt\n"
			+ "+++ b/heal.txt\n"
			+ "@@ -2,1 +2,1 @@\n"
			+ "-two\n"
			+ "+fixed\n";

	private static final VerifyConfig CONFIG =
			new VerifyConfig("mvn -B compile", "", "mvn -B verify", 0);

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
	void testTheShelvedChangeIsBuiltInAWorkspaceOfItsOwn() throws Exception {
		FreeStyleBuild build = buildJob("Heal-verify-built");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);
		long shelf = shelvedChange(build);

		AtomicReference<String> seen = new AtomicReference<>();
		AtomicReference<String> where = new AtomicReference<>();
		ShelfVerifier.Build recorder = (temp, command) -> {
			where.set(temp.getRemote());
			seen.set(temp.child("heal.txt").readToString());
			return true;
		};

		boolean built = verifier(build, recorder).verify(CREDENTIAL, clientOf(build),
				revisionOf(build), buildWorkspace.sibling("verify-me"), shelf);

		assertTrue(built);
		assertEquals(PATCHED, seen.get(), "the shelved fix must be what gets built");
		assertNotEquals(buildWorkspace.getRemote(), where.get(),
				"the shelf must be built somewhere the failed build never touched");
	}

	@Test
	void testAShelfThatDoesNotBuildIsReportedAsSuch() throws Exception {
		FreeStyleBuild build = buildJob("Heal-verify-failed");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		boolean built = verifier(build, (temp, command) -> false).verify(CREDENTIAL, clientOf(build),
				revisionOf(build), buildWorkspace.sibling("verify-fail"), shelvedChange(build));

		assertFalse(built);
	}

	@Test
	void testTheConsoleSaysWhetherCompileAndVerifyPassed() throws Exception {
		FreeStyleBuild build = buildJob("Heal-verify-console");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		verifier(build, (temp, command) -> !command.contains("verify")).verify(CREDENTIAL,
				clientOf(build), revisionOf(build), buildWorkspace.sibling("verify-console"),
				shelvedChange(build));

		String log = console.toString(StandardCharsets.UTF_8);
		assertTrue(log.contains("clean workspace: mvn -B compile — passed"), log);
		assertTrue(log.contains("clean workspace: mvn -B verify — failed"), log);
	}

	@Test
	void testTheVerifyCommandIsNotRunWhenTheCompileFails() throws Exception {
		FreeStyleBuild build = buildJob("Heal-verify-short-circuit");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		verifier(build, (temp, command) -> false).verify(CREDENTIAL, clientOf(build),
				revisionOf(build), buildWorkspace.sibling("verify-short"), shelvedChange(build));

		String log = console.toString(StandardCharsets.UTF_8);
		assertTrue(log.contains("clean workspace: mvn -B compile — failed"), log);
		assertFalse(log.contains("mvn -B verify — "),
				"there is nothing to test once the change will not compile");
	}

	@Test
	void testTheVerificationWorkspaceIsTakenAwayAfterwards() throws Exception {
		FreeStyleBuild build = buildJob("Heal-verify-cleanup");
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);
		FilePath temp = buildWorkspace.sibling("verify-gone");

		verifier(build, (t, command) -> true).verify(CREDENTIAL, clientOf(build), revisionOf(build),
				temp, shelvedChange(build));

		assertFalse(temp.exists(), "the throwaway workspace must not be left on the agent");

		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.getInstance(), CREDENTIAL, null)) {
			assertFalse(p4.isClient(clientOf(build) + "-heal"),
					"the throwaway client must not be left on the server");
		}
	}

	private ShelfVerifier verifier(FreeStyleBuild build, ShelfVerifier.Build howToBuild) {
		return new ShelfVerifier(build, null, listener(), new EnvVars(), CONFIG, howToBuild);
	}

	private static String clientOf(FreeStyleBuild build) {
		return TagAction.getLastAction(build).getClient();
	}

	private static P4Ref revisionOf(FreeStyleBuild build) {
		return TagAction.getLastAction(build).getRefChanges().get(0);
	}

	private long shelvedChange(FreeStyleBuild build) throws Exception {
		FilePath buildWorkspace = build.getWorkspace();
		assertNotNull(buildWorkspace);

		WorkspacePatchSession session = new WorkspacePatchSession(buildWorkspace, listener(), false,
				new ShelveDelivery(build, buildWorkspace, listener()));
		session.apply(UnifiedDiff.parse(FIX));
		String reference = session.deliver(UnifiedDiff.parse(FIX), "AI fix: off-by-one");

		// The first run of digits, not every digit: when the server advertises a Swarm
		// the reference also carries a review URL ending in the same change number,
		// and stripping non-digits would splice the two numbers together.
		Matcher digits = Pattern.compile("\\d+").matcher(reference);
		assertTrue(digits.find(), "no changelist number in: " + reference);
		return Long.parseLong(digits.group());
	}

	private FreeStyleBuild buildJob(String name) throws Exception {
		submitFile(jenkins, DEPOT_FILE, ORIGINAL);

		String client = "verify.ws";
		String view = "//depot/VerifyData/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		FreeStyleProject project = jenkins.createFreeStyleProject(name);
		Populate populate = new AutoCleanImpl();
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, build.getResult());
		return build;
	}
}
