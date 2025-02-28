package org.jenkinsci.plugins.p4.client;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class CheckoutTest extends DefaultEnvironment {

	private static final Logger logger = Logger.getLogger(CheckoutTest.class.getName());
	private static final String P4ROOT = "tmp-CheckoutTest-p4root";

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testCheckoutUnrestrictedView() throws Exception {
		String client = "CheckoutUnrestrictedView.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutUnrestrictedView");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl(true, true, false, false, false, "", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Request change 17 and expect change 17 as it's included in the workspace view
		final String requestedChangelist = "17";
		final String expectedChangelist = requestedChangelist;

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), requestedChangelist));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		Map<String, String> env = new HashMap<>();
		scm.buildEnvironment(build, env);

		// Assert that the workspace sync'd the expected change
		assertEquals(expectedChangelist, env.get("P4_CHANGELIST"));
	}

	@Issue("JENKINS-57534")
	@Test
	public void testCheckoutRestrictedView() throws Exception {
		String client = "CheckoutRestrictedView.ws";
		String view = "//depot/Main/... //" + client + "/Main/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutRestrictedView");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl(true, true, false, false, false, "", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Request change 17, but expect change 16 as change 17 is not included in the workspace view
		final String requestedChangelist = "17";
		final String expectedChangelist = "16";

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), requestedChangelist));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		Map<String, String> env = new HashMap<>();
		scm.buildEnvironment(build, env);

		// Assert that the workspace sync'd the expected change
		assertEquals(expectedChangelist, env.get("P4_CHANGELIST"));
	}

	@Test
	public void testChangesFromLastBuildPipeline() throws Exception {

		String base = "//depot/changes";
		String jfile = base + "/Jenkinsfile";
		String tfile = base + "/test.txt";

		String success = """
				
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        echo "Success"
				      }
				    }
				  }
				}""";

		String fail = """
				
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        error('Failed to build')
				      }
				    }
				  }
				}""";

		submitFile(jenkins, jfile, success);

		// Manual workspace spec definition
		String client = "changes.ws";
		String view = base + "/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "testChangesFromLastBuildPipeline");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// Run 1 (only one change on the first build) @45
		submitFile(jenkins, jfile, "//change1" + success);
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		assertEquals(1, run1.getChangeSets().size());
		P4ChangeSet cs1 = (P4ChangeSet)run1.getChangeSets().get(0);
		assertEquals(1, cs1.getHistory().size());

		// Run 2  @46 @47
		submitFile(jenkins, tfile, "//change2");
		submitFile(jenkins, jfile, "//change3" + success);
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		assertEquals(1, run1.getChangeSets().size());
		P4ChangeSet cs2 = (P4ChangeSet)run2.getChangeSets().get(0);
		assertEquals(2, cs2.getHistory().size());

		// Run 3  @48
		submitFile(jenkins, jfile, "//change4" + fail);
		WorkflowRun run3 = job.scheduleBuild2(0).get();
		assertEquals(1, run3.getChangeSets().size());
		P4ChangeSet cs3 = (P4ChangeSet)run3.getChangeSets().get(0);
		assertEquals(1, cs3.getHistory().size());

		// Run 4  @49
		submitFile(jenkins, jfile, "//change5" + fail);
		WorkflowRun run4 = job.scheduleBuild2(0).get();
		assertEquals(1, run4.getChangeSets().size());
		P4ChangeSet cs4 = (P4ChangeSet)run4.getChangeSets().get(0);
		assertEquals(1, cs4.getHistory().size());

		// Run 4  @50
		submitFile(jenkins, jfile, "//change6" + success);
		WorkflowRun run5 = job.scheduleBuild2(0).get();
		assertEquals(1, run5.getChangeSets().size());
		P4ChangeSet cs5 = (P4ChangeSet)run5.getChangeSets().get(0);
		assertEquals(1, cs5.getHistory().size());
	}

    @Test
    public void testChangesFromLastSuccesssPipeline() throws Exception {

		String base = "//depot/changes";
		String jfile = base + "/Jenkinsfile";
		String tfile = base + "/test.txt";

	    String success = """
			    
			    pipeline {
			      agent any
			      stages {
			        stage('Test') {
			          steps {
			            echo "Success"
			          }
			        }
			      }
			    }""";

	    String fail = """
			    
			    pipeline {
			      agent any
			      stages {
			        stage('Test') {
			          steps {
			            error('Failed to build')
			          }
			        }
			      }
			    }""";

		submitFile(jenkins, jfile, success);

		// Manual workspace spec definition
		String client = "changes.ws";
		String view = base + "/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		scm.getDescriptor().setLastSuccess(true);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "testChangesFromLastSuccesssPipeline");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// Run 1 (only one change on the first build) @45
		submitFile(jenkins, jfile, "//change1" + success);
		WorkflowRun run1 = job.scheduleBuild2(0).get();
		assertEquals(1, run1.getChangeSets().size());
		P4ChangeSet cs1 = (P4ChangeSet)run1.getChangeSets().get(0);
		assertEquals(1, cs1.getHistory().size());

		// Run 2  @46 @47
		submitFile(jenkins, tfile, "//change2");
		submitFile(jenkins, jfile, "//change3" + success);
		WorkflowRun run2 = job.scheduleBuild2(0).get();
		assertEquals(1, run1.getChangeSets().size());
		P4ChangeSet cs2 = (P4ChangeSet)run2.getChangeSets().get(0);
		assertEquals(2, cs2.getHistory().size());

		// Run 3  @48
		submitFile(jenkins, jfile, "//change4" + fail);
		WorkflowRun run3 = job.scheduleBuild2(0).get();
		assertEquals(1, run3.getChangeSets().size());
		P4ChangeSet cs3 = (P4ChangeSet)run3.getChangeSets().get(0);
		assertEquals(1, cs3.getHistory().size());

		// Run 4  @48 @49
		submitFile(jenkins, jfile, "//change5" + fail);
		WorkflowRun run4 = job.scheduleBuild2(0).get();
		assertEquals(1, run4.getChangeSets().size());
		P4ChangeSet cs4 = (P4ChangeSet)run4.getChangeSets().get(0);
		assertEquals(2, cs4.getHistory().size());

		// Run 4  @48 @49 @50
		submitFile(jenkins, jfile, "//change6" + success);
		WorkflowRun run5 = job.scheduleBuild2(0).get();
		assertEquals(1, run5.getChangeSets().size());
		P4ChangeSet cs5 = (P4ChangeSet)run5.getChangeSets().get(0);
		assertEquals(3, cs5.getHistory().size());
    }

	@Test
	public void testMatrixConfigurationManualWorkspace() throws Exception {

		// Multi-configuration project
		MatrixProject project = jenkins.createProject(MatrixProject.class, "matrix");
		AxisList axes = new AxisList();
		axes.add(new Axis("VARIANT", "v1", "v2"));
		project.setAxes(axes);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-matrix";
		String view = "//depot/${VARIANT}/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// Auto clean
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		submitFile(jenkins, "//depot/v1/src/file1", "content");
		submitFile(jenkins, "//depot/v2/src/file2", "content");

		project.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals(Result.SUCCESS, project.getLastBuild().getResult());
	}
}
