package org.jenkinsci.plugins.p4.tagging;

import com.perforce.p4java.core.ILabel;
import com.perforce.p4java.core.ILabelMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
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

import static com.perforce.p4java.core.IMapEntry.EntryType.EXCLUDE;
import static com.perforce.p4java.core.IMapEntry.EntryType.INCLUDE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class TaggingTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-FreeStyleTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
        jenkins.timeout = 30 * 60;
    }

    @BeforeEach
    void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void labelShouldGetCreatedByUsingLabelInPostBuild() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("LabelProject");
		String view = """
				//depot/Freestyle/... //${P4_CLIENT}/Freestyle/...
				-//depot/Freestyle/main/sub1/... //${P4_CLIENT}/Freestyle/main/sub1/...
				-//depot/Freestyle/main/sub2/... //${P4_CLIENT}/Freestyle/main/sub2/...
				-//depot/Freestyle/main/sub3/... //${P4_CLIENT}/Freestyle/main/sub3/...
				//depot/sub/... //${P4_CLIENT}/sub/...
				"//depot/sub sub/..." "//${P4_CLIENT}/sub sub/...\"""";

		WorkspaceSpec spec = new WorkspaceSpec(false, true, false, false, false, false, null, "LOCAL", view, null, null, null, false);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, "jenkins-${NODE_NAME}-${JOB_NAME}.ws", spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		TagNotifier tagNotifier = new TagNotifier("Label-1", "Perforce Test Label", false);
		project.getPublishersList().add(tagNotifier);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertEquals(Result.SUCCESS, build.getResult());
		jenkins.assertLogContains("Label Label-1 saved.", build);

		ViewMap<ILabelMapping> viewMapping;
		try (ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null)) {
			IOptionsServer server = p4.getConnection();
			ILabel label = server.getLabel("Label-1");
			viewMapping = label.getViewMapping();
		}

		assertEquals(INCLUDE, viewMapping.getEntry(0).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(1).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(2).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(3).getType());
		assertEquals(INCLUDE, viewMapping.getEntry(4).getType());
		assertEquals(INCLUDE, viewMapping.getEntry(5).getType());
	}

	@Test
	void labelShouldGetCreatedOnPromotedBuildViaTagNotifier() throws Exception {
		// Job A: the real Perforce job whose build gets a TagAction attached at checkout.
		FreeStyleProject jobA = jenkins.createFreeStyleProject("PromotedSource");
		String client = "promoted-source.ws";
		String view = "//depot/Freestyle/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		jobA.setScm(scm);
		jobA.save();

		FreeStyleBuild buildA = jobA.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, buildA.getResult());

		// Job B: the "promotion" job, no P4 SCM of its own. TagNotifier must fall back to
		// PROMOTED_JOB_NAME/PROMOTED_NUMBER (as the Promoted Builds plugin would set them)
		// to find Job A's TagAction, since Job B's own build has none.
		FreeStyleProject jobB = jenkins.createFreeStyleProject("PromotionJob");
		TagNotifier tagNotifier = new TagNotifier("Promoted-Label-1", "Promoted Test Label", false);
		jobB.getPublishersList().add(tagNotifier);
		jobB.save();

		List<ParameterValue> internal = new ArrayList<>();
		internal.add(new StringParameterValue("PROMOTED_JOB_NAME", jobA.getFullName()));
		internal.add(new StringParameterValue("PROMOTED_NUMBER", String.valueOf(buildA.getNumber())));
		Action actions = new SafeParametersAction(new ArrayList<>(), internal);

		FreeStyleBuild buildB = jobB.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
		assertEquals(Result.SUCCESS, buildB.getResult());

		try (ConnectionHelper p4 = new ConnectionHelper(jobA, CREDENTIAL, null)) {
			IOptionsServer server = p4.getConnection();
			ILabel label = server.getLabel("Promoted-Label-1");
			assertNotNull(label, "the promoted-build lookup should have labelled Job A's TagAction");
		}
	}

	@Test
	void labelShouldGetCreatedOnPromotedBuildViaP4TagPipelineStep() throws Exception {
		// Job A: the real Perforce job whose build gets a TagAction attached at checkout.
		FreeStyleProject jobA = jenkins.createFreeStyleProject("PromotedSourcePipeline");
		String client = "promoted-source-pipeline.ws";
		String view = "//depot/Freestyle/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		jobA.setScm(scm);
		jobA.save();

		FreeStyleBuild buildA = jobA.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, buildA.getResult());

		// Job B: a pipeline "promotion" job with no P4 SCM, using the p4tag step
		// (TagNotifierStep) which must fall back to PROMOTED_JOB_NAME/PROMOTED_NUMBER.
		WorkflowJob jobB = jenkins.jenkins.createProject(WorkflowJob.class, "PromotionPipelineJob");
		jobB.setDefinition(new CpsFlowDefinition(
				"node {\n" +
						"  p4tag rawLabelName: 'Promoted-Label-2', rawLabelDesc: 'Promoted Pipeline Label'\n" +
						"}", true));

		List<ParameterValue> internal = new ArrayList<>();
		internal.add(new StringParameterValue("PROMOTED_JOB_NAME", jobA.getFullName()));
		internal.add(new StringParameterValue("PROMOTED_NUMBER", String.valueOf(buildA.getNumber())));
		Action paramsAction = new SafeParametersAction(new ArrayList<>(), internal);

		WorkflowRun runB = jobB.scheduleBuild2(0, paramsAction).get();
		jenkins.assertBuildStatusSuccess(runB);

		try (ConnectionHelper p4 = new ConnectionHelper(jobA, CREDENTIAL, null)) {
			IOptionsServer server = p4.getConnection();
			ILabel label = server.getLabel("Promoted-Label-2");
			assertNotNull(label, "the p4tag step's promoted-build lookup should have labelled Job A's TagAction");
		}
	}
}
