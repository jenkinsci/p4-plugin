package org.jenkinsci.plugins.p4.heal;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.Maven;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.heal.provider.AiProvider;
import org.jenkinsci.plugins.p4.heal.provider.AiRequest;
import org.jenkinsci.plugins.p4.heal.provider.AiResult;
import org.jenkinsci.plugins.p4.heal.provider.AiTool;
import org.jenkinsci.plugins.p4.heal.provider.ClaudeProviderImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class HealNotifierTest {

	private static PerforceScm.DescriptorImpl scmDescriptor(JenkinsRule jenkins) {
		return jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);
	}

	private static HealNotifier notifier() {
		HealNotifier notifier = new HealNotifier(new ClaudeProviderImpl("anthropic-key"));
		notifier.setCompileCommand("mvn -B compile");
		notifier.setVerifyCommand("mvn -B verify");
		notifier.setTargetedTestCommand("mvn -B test -Dtest={tests}");
		notifier.setAllowedPaths("src/main/java/,src/test/java/");
		notifier.setMaxAttempts(2);
		notifier.setFlakeReruns(1);
		notifier.setMaxTokens(500_000);
		return notifier;
	}

	@Test
	void testHealingIsOffUntilAnAdministratorEnablesIt(JenkinsRule jenkins) {
		assertFalse(scmDescriptor(jenkins).isAiHealEnabled(),
				"AI healing must be off out of the box");
	}

	@Test
	void testBuildStepIsHiddenWhileTheFeatureIsDisabled(JenkinsRule jenkins) {
		scmDescriptor(jenkins).setAiHealEnabled(false);

		HealNotifier.DescriptorImpl descriptor =
				jenkins.jenkins.getDescriptorByType(HealNotifier.DescriptorImpl.class);

		assertNotNull(descriptor);
		assertFalse(descriptor.isApplicable(FreeStyleProject.class),
				"the step must not be offered while the global switch is off");
	}

	@Test
	void testBuildStepAppearsOnceEnabled(JenkinsRule jenkins) {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		HealNotifier.DescriptorImpl descriptor =
				jenkins.jenkins.getDescriptorByType(HealNotifier.DescriptorImpl.class);

		assertTrue(descriptor.isApplicable(FreeStyleProject.class));
		assertEquals("P4: Attempt an AI fix when the build fails", descriptor.getDisplayName());
	}

	@Test
	void testDefaultsAreSafe() {
		HealNotifier notifier = new HealNotifier(new ClaudeProviderImpl("key"));

		assertTrue(notifier.isDryRun(), "a fresh step must not be able to touch the depot");
		assertFalse(notifier.isAllowBuildConfigChanges());
		assertEquals("src/", notifier.getAllowedPaths());
		assertTrue(notifier.getFlakeReruns() > 0, "flake checking should be on by default");
		assertEquals("", notifier.getVerifyCommand(),
				"blank out of the box, so the job's own build step is used");
		assertEquals("", notifier.getCompileCommand());
	}

	@Test
	void testConfigurationSurvivesASaveAndReload(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		FreeStyleProject project = jenkins.createFreeStyleProject("heal-roundtrip");
		project.getPublishersList().add(notifier());

		jenkins.configRoundtrip(project);

		HealNotifier reloaded = project.getPublishersList().get(HealNotifier.class);
		assertNotNull(reloaded, "the step did not survive a config round-trip");
		assertEquals("mvn -B compile", reloaded.getCompileCommand());
		assertEquals("mvn -B test -Dtest={tests}", reloaded.getTargetedTestCommand());
		assertEquals("", reloaded.getVerifyCommand(),
				"the full build is the job's own build step, so it is not a form field");
		assertEquals("src/main/java/,src/test/java/", reloaded.getAllowedPaths());
		assertEquals(2, reloaded.getMaxAttempts());
		assertEquals(1, reloaded.getFlakeReruns());
		assertEquals(500_000, reloaded.getMaxTokens());
		assertTrue(reloaded.isDryRun());
	}

	@Test
	void testProviderChoiceSurvivesASaveAndReload(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		FreeStyleProject project = jenkins.createFreeStyleProject("heal-provider-roundtrip");
		project.getPublishersList().add(notifier());

		jenkins.configRoundtrip(project);

		HealNotifier reloaded = project.getPublishersList().get(HealNotifier.class);
		assertTrue(reloaded.getAiProvider() instanceof ClaudeProviderImpl);
		assertEquals("anthropic-key",
				((ClaudeProviderImpl) reloaded.getAiProvider()).getCredentialId());
	}

	/**
	 * {@link Result#ABORTED} and {@link Result#NOT_BUILT} sort <em>worse</em> than
	 * {@link Result#FAILURE}, so a naive "not better than FAILURE" test starts a
	 * heal for a build the user cancelled.
	 */
	@Test
	void testOnlyAFailedBuildIsWorthHealing() {
		assertTrue(HealNotifier.shouldHeal(Result.FAILURE));

		assertFalse(HealNotifier.shouldHeal(Result.SUCCESS));
		assertFalse(HealNotifier.shouldHeal(Result.UNSTABLE));
		assertFalse(HealNotifier.shouldHeal(Result.ABORTED), "a cancelled build is not a failure");
		assertFalse(HealNotifier.shouldHeal(Result.NOT_BUILT));
		assertFalse(HealNotifier.shouldHeal(null), "a build still running has no verdict yet");
	}

	/**
	 * Healing is best-effort. Anything it throws — not just {@link IOException} —
	 * has to stay inside the step, or a build that already failed gets its verdict
	 * rewritten by the thing that was supposed to help it.
	 */
	@Test
	void testAnUnexpectedFailureInsideHealingIsContained(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		FreeStyleProject project = jenkins.createFreeStyleProject("heal-contains-runtime-failure");
		project.getBuildersList().add(new FailureBuilder());
		FreeStyleBuild build = jenkins.buildAndAssertStatus(Result.FAILURE, project);

		HealNotifier notifier = exploding("true", "true");
		ByteArrayOutputStream log = new ByteArrayOutputStream();
		TaskListener listener = new StreamTaskListener(log, StandardCharsets.UTF_8);

		notifier.attemptHeal(build, build.getWorkspace(), jenkins.createLocalLauncher(), listener);

		assertEquals(Result.FAILURE, build.getResult(), "healing must not change the verdict");
		assertTrue(log.toString(StandardCharsets.UTF_8).contains("[p4-heal] healing could not run"),
				log.toString(StandardCharsets.UTF_8));
	}

	/**
	 * A provider that fails the way a misconfigured one does: with something that
	 * is not an {@link IOException}.
	 */
	private static final class ExplodingProvider extends AiProvider {

		@Serial
		private static final long serialVersionUID = 1L;

		@Override
		public AiResult converse(AiRequest request, List<AiTool> tools) {
			throw new IllegalStateException("invalid header value");
		}
	}

	/**
	 * A step whose provider must never be reached, or the test would need a real
	 * API key. Anything past the commands being worked out shows up as the
	 * containment message.
	 */
	private static HealNotifier exploding(String compileCommand, String verifyCommand) {
		HealNotifier notifier = new HealNotifier(new ExplodingProvider());
		notifier.setCompileCommand(compileCommand);
		notifier.setVerifyCommand(verifyCommand);
		return notifier;
	}

	/**
	 * A job that fails before it ever reaches its Maven step, so the step is only
	 * ever read as configuration.
	 */
	private static FreeStyleProject failingMavenJob(JenkinsRule jenkins, String name)
			throws Exception {

		FreeStyleProject project = jenkins.createFreeStyleProject(name);
		project.getBuildersList().add(new FailureBuilder());
		project.getBuildersList().add(new Maven("clean install", null));
		return project;
	}

	private static String healAndCapture(JenkinsRule jenkins, HealNotifier notifier,
	                                     FreeStyleProject project) throws Exception {

		FreeStyleBuild build = jenkins.buildAndAssertStatus(Result.FAILURE, project);
		ByteArrayOutputStream log = new ByteArrayOutputStream();
		notifier.attemptHeal(build, build.getWorkspace(), jenkins.createLocalLauncher(),
				new StreamTaskListener(log, StandardCharsets.UTF_8));
		return log.toString(StandardCharsets.UTF_8);
	}

	@Test
	void testBlankCommandsAreTakenFromTheJobsOwnBuildStep(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		String log = healAndCapture(jenkins, exploding("", ""),
				failingMavenJob(jenkins, "heal-inherits-commands"));

		assertTrue(log.contains("[p4-heal] verify: mvn -B clean install"),
				"the fix must be proved against the build the job actually runs\n" + log);
		assertTrue(log.contains("[p4-heal] compile: mvn -B compile"), log);
		assertTrue(log.contains("[p4-heal] targeted tests: mvn -B test -Dtest={tests}"), log);
	}

	@Test
	void testAConfiguredCommandWinsOverTheJobs(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		String log = healAndCapture(jenkins, exploding("make", "make check"),
				failingMavenJob(jenkins, "heal-overrides-commands"));

		assertTrue(log.contains("[p4-heal] verify: make check"), log);
		assertTrue(log.contains("[p4-heal] compile: make"), log);
		assertFalse(log.contains("mvn -B clean install"), "the override must not be ignored\n" + log);
	}

	/**
	 * A blank verify command would make every later gate pass on nothing, so a fix
	 * nobody checked could be shelved. Healing has to decline instead.
	 */
	@Test
	void testHealingDeclinesWhenThereIsNothingToVerifyWith(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		FreeStyleProject project = jenkins.createFreeStyleProject("heal-no-verify-command");
		project.getBuildersList().add(new FailureBuilder());

		String log = healAndCapture(jenkins, exploding("", ""), project);

		assertTrue(log.contains("Nothing to verify a fix with"), log);
		assertFalse(log.contains("[p4-heal] healing could not run"),
				"the provider must not be reached at all\n" + log);
	}

	@Test
	void testHealingIsSkippedEntirelyOnASuccessfulBuild(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		FreeStyleProject project = jenkins.createFreeStyleProject("heal-success");
		project.getPublishersList().add(notifier());

		jenkins.assertLogNotContains("[p4-heal]",
				jenkins.buildAndAssertSuccess(project));
	}
}
