package org.jenkinsci.plugins.p4.heal.verify;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jenkins is needed even for the string building: {@code hudson.tasks.Maven}'s
 * constructor reads the global Maven configuration.
 */
@WithJenkins
class JobCommandsTest {

	private static final String MVN = "/opt/maven/bin/mvn";

	private static JobCommands of(Maven maven, EnvVars environment) throws IOException {
		return JobCommands.of(maven, environment, MVN);
	}

	@Test
	void testDerivesEveryCommandFromTheJobsGoals(JenkinsRule jenkins) throws IOException {
		JobCommands commands = of(new Maven("clean install", null), new EnvVars());

		assertEquals(MVN + " -B clean install", commands.verify(),
				"verify must run exactly what the job runs");
		assertEquals(MVN + " -B compile", commands.compile());
		assertEquals(MVN + " -B test -Dtest={tests}", commands.targeted());
	}

	@Test
	void testKeepsTheTestsPlaceholderForTheLadderToSubstitute(JenkinsRule jenkins)
			throws IOException {

		VerifyConfig config = new VerifyConfig("", of(new Maven("verify", null), new EnvVars())
				.targeted(), "", 0);

		assertEquals(MVN + " -B test -Dtest=OneTest,TwoTest",
				config.targetedCommandFor("OneTest,TwoTest"));
	}

	@Test
	void testQuotesAnExecutablePathWithSpaces(JenkinsRule jenkins) throws IOException {
		JobCommands commands =
				JobCommands.of(new Maven("verify", null), new EnvVars(), "/Program Files/mvn");

		assertEquals("\"/Program Files/mvn\" -B verify", commands.verify());
		assertEquals("/Program Files/mvn", Util.tokenize(commands.verify())[0],
				"the quoting must survive the tokenizer BuildVerifier launches with");
	}

	@Test
	void testCarriesThePomAndPropertiesTheJobWasGiven(JenkinsRule jenkins) throws IOException {
		Maven maven = new Maven("verify", null, "sub/pom.xml", "skipITs=true", null);

		assertEquals(MVN + " -B -f sub/pom.xml -DskipITs=true verify", of(maven, new EnvVars())
				.verify());
	}

	@Test
	void testUsesThePrivateRepositoryInsideWhicheverWorkspaceRuns(JenkinsRule jenkins)
			throws IOException {

		Maven maven = new Maven("verify", null, null, null, null, true);

		// Relative, so it lands in the build's workspace and in the clean workspace
		// the shelf is verified in, exactly as it would for the job itself.
		assertEquals(MVN + " -B -Dmaven.repo.local=.repository verify",
				of(maven, new EnvVars()).verify());
	}

	@Test
	void testExpandsEnvironmentVariables(JenkinsRule jenkins) throws IOException {
		EnvVars environment = new EnvVars("GOAL", "install", "MODULE", "sub");
		Maven maven = new Maven("clean $GOAL", null, "$MODULE/pom.xml", null, null);

		assertEquals(MVN + " -B -f sub/pom.xml clean install", of(maven, environment).verify());
	}

	@Test
	void testCollapsesGoalsWrittenOverSeveralLines(JenkinsRule jenkins) throws IOException {
		assertEquals(MVN + " -B clean install",
				of(new Maven("clean\n\tinstall", null), new EnvVars()).verify());
	}

	@Test
	void testFindsTheMavenBuildStepOfAFreestyleJob(JenkinsRule jenkins) throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject();
		project.getBuildersList().add(new Shell("echo before"));
		project.getBuildersList().add(new Maven("clean install", null));

		Optional<Maven> found = JobCommands.mavenStep(project);

		assertTrue(found.isPresent(), "a Maven build step must be found past other steps");
		assertEquals("clean install", found.get().getTargets());
	}

	@Test
	void testFindsNothingWhenTheJobHasNoMavenBuildStep(JenkinsRule jenkins) throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject();
		project.getBuildersList().add(new Shell("make all"));

		assertFalse(JobCommands.mavenStep(project).isPresent(),
				"a shell script cannot be turned into a command line safely");
	}
}
