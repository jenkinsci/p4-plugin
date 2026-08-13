package org.jenkinsci.plugins.p4.workflow;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.heal.provider.ClaudeProviderImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class P4HealStepTest {

	private static PerforceScm.DescriptorImpl scmDescriptor(JenkinsRule jenkins) {
		return jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);
	}

	private static P4HealStep step() {
		P4HealStep step = new P4HealStep(
				new ClaudeProviderImpl("anthropic-key"), "mvn -B compile", "mvn -B verify");
		step.setTargetedTestCommand("mvn -B test -Dtest={tests}");
		step.setAllowedPaths("src/main/java/");
		step.setMaxAttempts(2);
		step.setFlakeReruns(1);
		step.setMaxTokens(250_000);
		return step;
	}

	@Test
	void testStepIsRegisteredAsP4heal(JenkinsRule jenkins) {
		P4HealStep.DescriptorImpl descriptor =
				jenkins.jenkins.getDescriptorByType(P4HealStep.DescriptorImpl.class);

		assertEquals("p4heal", descriptor.getFunctionName());
		assertTrue(descriptor.getRequiredContext().size() >= 4,
				"the step needs a run, workspace, launcher and listener");
	}

	@Test
	void testDefaultsAreSafe() {
		P4HealStep step = new P4HealStep(new ClaudeProviderImpl("key"), "compile", "verify");

		assertTrue(step.isDryRun(), "a fresh step must not be able to touch the depot");
		assertFalse(step.isAllowBuildConfigChanges());
		assertEquals("src/", step.getAllowedPaths());
	}

	@Test
	void testEveryOptionIsReadableBack() {
		P4HealStep step = step();

		assertEquals("mvn -B compile", step.getCompileCommand());
		assertEquals("mvn -B verify", step.getVerifyCommand());
		assertEquals("mvn -B test -Dtest={tests}", step.getTargetedTestCommand());
		assertEquals("src/main/java/", step.getAllowedPaths());
		assertEquals(2, step.getMaxAttempts());
		assertEquals(1, step.getFlakeReruns());
		assertEquals(250_000, step.getMaxTokens());
		assertEquals("anthropic-key",
				((ClaudeProviderImpl) step.getAiProvider()).getCredentialId());
	}

	/**
	 * Binds the step the way a real pipeline does — named parameters through
	 * Groovy — which is what proves the data-bound constructor and setters are
	 * wired correctly.
	 */
	@Test
	void testEveryOptionCanBeSetFromPipelineSyntax(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "heal-full-syntax");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  p4heal aiProvider: claude(credentialId: 'anthropic-key'),\n"
				+ "         compileCommand: 'true',\n"
				+ "         verifyCommand: 'true',\n"
				+ "         targetedTestCommand: 'true',\n"
				+ "         allowedPaths: 'src/',\n"
				+ "         dryRun: true,\n"
				+ "         maxAttempts: 1,\n"
				+ "         criticQuorum: 2,\n"
				+ "         flakeReruns: 0,\n"
				+ "         maxTokens: 1000,\n"
				+ "         smartModel: 'claude-opus-5',\n"
				+ "         cheapModel: 'claude-haiku-4-5',\n"
				+ "         smartEffort: 'high',\n"
				+ "         cheapEffort: 'low',\n"
				+ "         maxResponseTokens: 8000\n"
				+ "}\n", true));

		jenkins.buildAndAssertSuccess(job);
	}

	@Test
	void testStepSaysSoWhenHealingIsDisabledGlobally(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(false);

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "heal-disabled");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  p4heal aiProvider: claude(credentialId: 'anthropic-key'),\n"
				+ "         compileCommand: 'true', verifyCommand: 'true'\n"
				+ "}\n", true));

		WorkflowRun run = jenkins.buildAndAssertSuccess(job);

		jenkins.assertLogContains("AI healing is disabled globally", run);
	}

	@Test
	void testStepRunsWhenEnabledEvenThoughTheBuildHasNotFailedYet(JenkinsRule jenkins)
			throws Exception {

		scmDescriptor(jenkins).setAiHealEnabled(true);

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "heal-enabled");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  p4heal aiProvider: claude(credentialId: 'missing-credential'),\n"
				+ "         compileCommand: 'true', verifyCommand: 'true'\n"
				+ "}\n", true));

		WorkflowRun run = jenkins.buildAndAssertSuccess(job);

		// Calling the step is the decision to heal; it must not silently no-op just
		// because currentBuild.result has not been set yet.
		jenkins.assertLogNotContains("disabled globally", run);
		jenkins.assertLogContains("[p4-heal]", run);
	}

	@Test
	void testAMissingCredentialFailsTheHealNotTheBuild(JenkinsRule jenkins) throws Exception {
		scmDescriptor(jenkins).setAiHealEnabled(true);

		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "heal-no-credential");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  p4heal aiProvider: claude(credentialId: 'does-not-exist'),\n"
				+ "         compileCommand: 'true', verifyCommand: 'true'\n"
				+ "}\n", true));

		// The build stays green: healing is best-effort and must never turn a
		// working pipeline red.
		jenkins.buildAndAssertSuccess(job);
	}
}
