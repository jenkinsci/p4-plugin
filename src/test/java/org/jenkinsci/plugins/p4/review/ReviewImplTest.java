package org.jenkinsci.plugins.p4.review;

import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class ReviewImplTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ReviewImplTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
    }

    @BeforeEach
    void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testStaticReviewImpl() throws Exception {
		String client = defaultClient();
		FreeStyleProject project = jenkins.createFreeStyleProject("StaticReview");
		Workspace workspace = new StaticWorkspaceImpl("none", false, client);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		HtmlPage page = jenkins.createWebClient().getPage(project, "review");
		HtmlElement review = page.getElementByName("review");
		assertEquals("text", review.getAttribute("type"));
	}

	@Test
	void testReviewEnvironmentVar() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "reviewEnvVar");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node() {\n" +
				"    stage('Sync files...') {\n" +
				"        checkout([$class: 'PerforceScm', " +
				"           credential: '" + CREDENTIAL + "', " +
				"           populate: [$class: 'ForceCleanImpl', have: false, pin: '', quiet: true], " +
				"           workspace: [$class: 'StreamWorkspaceImpl', charset: 'none', " +
				"              format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, " +
				"              streamName: '//stream/main']])\n" +
				"    }\n" +
				"    stage ('Test env...') {\n" +
				"        println \"P4_CHANGELIST=${env.P4_CHANGELIST}\"\n" +
				"        println \"P4_REVIEW=${env.P4_REVIEW}\"\n" +
				"        println \"P4_REVIEW_TYPE=${env.P4_REVIEW_TYPE}\"\n" +
				"    }\n" +
				"}", false));
		job.save();

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_REVIEW.toString(), "19"));
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "shelved"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		WorkflowRun run = job.scheduleBuild2(0, actions).get();
		jenkins.assertLogContains("P4_CHANGELIST=39", run);
		jenkins.assertLogContains("P4_REVIEW=19", run);
		jenkins.assertLogContains("P4_REVIEW_TYPE=SHELVED", run);
	}
}
