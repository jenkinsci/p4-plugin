package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.option.server.CounterOptions;
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
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.JsonHttpStubServer;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewNotifier;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class CheckoutTest extends DefaultEnvironment {

	private static final Logger LOGGER = Logger.getLogger(CheckoutTest.class.getName());
	private static final String P4ROOT = "tmp-CheckoutTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
        jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testCheckoutUnrestrictedView() throws Exception {
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

	@Test
	void testCheckoutAppliesPathFilterToChangelog() throws Exception {
		String client = "PathFilter.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PathFilter");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		// Includes a non-path filter too, to exercise applyPathFilters' skip-non-FilterPathImpl branch.
		List<Filter> filter = List.of(new FilterUserImpl("someoneelse"), new FilterPathImpl("//depot/PathFilter/exclude/"));
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Baseline build so the next changes show up as "new" in the following build's changelog.
		submitFile(jenkins, "//depot/PathFilter/keep/baseline.txt", "content");
		FreeStyleBuild build1 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build1.getResult());

		// A change entirely under the excluded path, and one outside it, in the same changelog.
		submitFile(jenkins, "//depot/PathFilter/exclude/fileA.txt", "content", "Change to exclude path");
		submitFile(jenkins, "//depot/PathFilter/keep/fileB.txt", "content", "Change to keep path");

		FreeStyleBuild build2 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build2.getResult());

		P4ChangeSet cs = (P4ChangeSet) build2.getChangeSets().get(0);
		assertEquals(1, cs.getHistory().size(),
				"the change entirely under the excluded path should be filtered out");
		assertTrue(cs.getHistory().get(0).getMsg().contains("Change to keep path"));
	}

	@Issue("JENKINS-66648")
	@Test
	void testCheckoutBuildLabelNowResolvesToHead() throws Exception {
		String client = "CheckoutLabelNow.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutLabelNow");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		String latestChange = submitFile(jenkins, "//depot/CheckoutLabelNow/file.txt", "content");

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_LABEL.toString(), "now"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: " + latestChange));
	}

	@Test
	void testCheckoutBuildLabelResolvesViaCounterWhenNotALabel() throws Exception {
		String client = "CheckoutCounterLabel.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutCounterLabel");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		P4PasswordImpl admin = createCredentials("admin", "Password", p4d.getRshPort(), "checkoutCounterLabel-admin");
		try (ConnectionHelper adminHelper = new ConnectionHelper(admin, null)) {
			adminHelper.getConnection().setCounter("checkoutCounterLabel", "10", new CounterOptions());
		}

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_LABEL.toString(), "checkoutCounterLabel"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 10"));
	}

	@Issue("JENKINS-58161")
	@Test
	void testCredentialTickCreatesBackgroundKeepAlive() throws Exception {
		String client = "CheckoutTick.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		P4PasswordImpl tickCredential = new P4PasswordImpl(CredentialsScope.GLOBAL, "checkoutTickCred",
				"desc", p4d.getRshPort(), null, "jenkins", "0", "0", null, "jenkins");
		tickCredential.setTick("50");
		SystemCredentialsProvider.getInstance().getCredentials().add(tickCredential);
		SystemCredentialsProvider.getInstance().save();

		FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutTick");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm("checkoutTickCred", workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.stream().anyMatch(line -> line.contains("...tick...")),
				"a non-zero credential tick should start a background keep-alive ticker during the task");
	}

	@Issue("JENKINS-57534")
	@Test
	void testCheckoutRestrictedView() throws Exception {
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
	void testChangesFromLastBuildPipeline() throws Exception {
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
	void testChangesFromLastSuccesssPipeline() throws Exception {
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
	void testMatrixConfigurationManualWorkspace() throws Exception {
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

	@Test
	void testMatrixConfigurationPollingChecksEachChildConfiguration() throws Exception {
		// Multi-configuration project
		MatrixProject project = jenkins.createProject(MatrixProject.class, "matrixPolling");
		AxisList axes = new AxisList();
		axes.add(new Axis("VARIANT", "v1", "v2"));
		project.setAxes(axes);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-matrixPolling";
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

		// A new change lands in one variant's path only.
		submitFile(jenkins, "//depot/v2/src/file3", "content");

		Logger polling = Logger.getLogger("MatrixPolling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);

		project.poll(listener);

		assertTrue(pollHandler.getLogBuffer().contains("VARIANT-v1"), "buffer=" + pollHandler.getLogBuffer());
		assertTrue(pollHandler.getLogBuffer().contains("VARIANT-v2"), "buffer=" + pollHandler.getLogBuffer());
	}

	@Test
	void testSwarmUpdateNotifiesRunningDuringBuildAndPassOnCompletion() throws Exception {
		String base = "//depot/SwarmUpdate";
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}";

		submitFile(jenkins, base + "/file1", "content");

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "swarmUpdateNotify");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				// Attach the progress message and stay 'building' past onStarted's first
				// 2-second timer tick, so ReviewNotifier's TimerTask also notifies "running"
				// (not just onCompleted's final "pass"/"fail").
				+ "    p4SwarmUpdate(updateMessage: 'in progress')\n"
				+ "    sleep(time: 3, unit: 'SECONDS')\n"
				+ "    checkout perforce(\n"
				+ "        credential: '" + CREDENTIAL + "', \n"
				+ "        populate: autoClean(quiet: true),\n"
				+ "        workspace: manualSpec(name: '" + client + "', \n"
				+ "           spec: clientSpec(view: '" + base + "/... //${P4_CLIENT}/...')))\n"
				+ "}", false));

		try (JsonHttpStubServer stub = new JsonHttpStubServer()) {
			stub.stub("/update", 200, "{}");

			List<ParameterValue> list = new ArrayList<>();
			list.add(new StringParameterValue(ReviewProp.SWARM_UPDATE.getProp(), stub.getUrl() + "/update"));
			SafeParametersAction actions = new SafeParametersAction(new ArrayList<>(), list);

			Logger reviewLogger = Logger.getLogger(ReviewNotifier.class.getName());
			TestHandler reviewHandler = new TestHandler();
			reviewLogger.addHandler(reviewHandler);

			WorkflowRun run = job.scheduleBuild2(0, actions).get();

			assertEquals(Result.SUCCESS, run.getResult());
			assertTrue(reviewHandler.getLogBuffer().contains("Response code: 200"), "buffer=" + reviewHandler.getLogBuffer());
			assertTrue(stub.getLastRequestBody("/update").contains("in progress"),
					"body=" + stub.getLastRequestBody("/update"));
		}
	}
}
