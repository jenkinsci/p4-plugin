package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.server.IOptionsServer;
import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.LogTaskListener;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.ExtendedJenkinsRule;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPatternListImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterViewMaskImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.scm.BranchesScmSource;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.TemplateWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PollingTest extends DefaultEnvironment {

	private static final Logger logger = Logger.getLogger(PollingTest.class.getName());
	private static final String P4ROOT = "tmp-PollingTest-p4root";

	@ClassRule
	public static ExtendedJenkinsRule jenkins = new ExtendedJenkinsRule(15 * 60);

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testPollingCounterPin() throws Exception {

		String client = "PollingCounterPin.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingCounterPin");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		Populate populate = new AutoCleanImpl(true, true, false, false, false, "change", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 4"));
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 40"));
	}

	@Test
	public void testPollingPin() throws Exception {

		String client = "PollingPin.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingPin");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, false, "auto15", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 4"));
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 15"));
	}

	@Test
	public void testPollingInc() throws Exception {

		String client = "PollingInc.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingInc");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, false, "auto15", null);
		List<Filter> filter = new ArrayList<>();
		FilterPerChangeImpl inc = new FilterPerChangeImpl(true);
		filter.add(inc);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		PollingResult found = project.poll(listener);
		assertEquals(PollingResult.BUILD_NOW, found);

		// Build now
		build = project.scheduleBuild2(0, cause).get();
		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 4"));

		// JENKINS-66169: disable poll per change
		List<ParameterValue> list2 = new ArrayList<>();
		list2.add(new StringParameterValue("P4_INCREMENTAL", "false"));
		Action actions2 = new SafeParametersAction(new ArrayList<>(), list2);

		Cause.UserIdCause cause2 = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause2, actions2).get();
		List<String> log2 = build.getLog(LOG_LIMIT);
		assertEquals(Result.SUCCESS, build.getResult());
		assertTrue(log2.contains("P4 Task: syncing files at change: 15"));
	}

	@Test
	public void testPollingMask() throws Exception {

		String client = "PollingMask.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingMask");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl();
		List<Filter> filter = new ArrayList<>();

		// Filter changes outside of //depot/Data path
		FilterViewMaskImpl mask = new FilterViewMaskImpl("//depot/Data");
		filter.add(mask);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 18"));
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 17"));
	}

	@Test
	public void testPollingMaskExcl() throws Exception {

		String client = "PollingMaskExcl.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingMaskExcl");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl();
		List<Filter> filter = new ArrayList<>();

		// Filter changes outside of //depot/Main and also under //depot/Main/TPI-83
		String sb = "//depot/Main" +
				"\n" +
				"-//depot/Main/TPI-83";

		FilterViewMaskImpl mask = new FilterViewMaskImpl(sb);
		filter.add(mask);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 16"));
		assertThat(pollHandler.getLogBuffer(), containsString("found change: 4"));
	}

	@Test
	public void testPatternList() throws Exception {

		String client = "PatternList.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternList");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl();
		List<Filter> filter = new ArrayList<>();

		// Only poll on 0 && .dat files's
		String sb = "//depot/main/[a-z]+-0\\.txt" +
				"\n" +
				".*\\.dat$";

		FilterPatternListImpl pList = new FilterPatternListImpl(sb, false);
		filter.add(pList);

		/* Should result in the follow changes captured:
		 * 8: 0
		 * 14: 0
		 * 17: .dat files are added
		 * 18: //depot/Data/file-1.dat
		 * Total: 4 changes
		 */
		long[] changesExpected = {18L, 17L, 14L, 8L};

		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);

		// Test all expected changes
		for (long c : changesExpected) {
			assertThat(pollHandler.getLogBuffer(), containsString("found change: " + c));
		}
	}

	@Test
	public void testPatternListCaseSensitive() throws Exception {
		String client = "PatternListCaseSensitive.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternListCaseSensitive");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl();
		List<Filter> filter = new ArrayList<>();

		// Only poll on a main with lower case 'm' (doesn't actually exist) -- ensure case sensitive is TRUE!
		FilterPatternListImpl pList = new FilterPatternListImpl("//depot/main/file-.*\\.txt", true);
		filter.add(pList);

		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), not(containsString("found change")));
	}

	@Test
	public void testPatternListPipeline() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "patternListPipeline");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "    checkout perforce(\n" +
				"        credential: '" + CREDENTIAL + "', \n" +
				"        filter: [viewPattern(caseSensitive: false, patternText: '//depot/main/foo.*')], \n" +
				"        populate: forceClean(quiet: true),\n" +
				"        workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n" +
				"           spec: clientSpec(view: '//depot/main/... //${P4_CLIENT}/...')))"
				+ "}", false));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);

		// Submit a change viable polling...
		submitFile(jenkins, "//depot/main/foo.001", "content");

		Logger polling = Logger.getLogger("Polling Test1");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);

		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		PollingResult poll1 = job.poll(listener);
		assertEquals(PollingResult.BUILD_NOW, poll1);

		assertThat(pollHandler.getLogBuffer(), containsString("found change: 44"));

		// Build to clear last change
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// Submit a change hidden from polling...
		submitFile(jenkins, "//depot/main/bar.002", "content");

		PollingResult poll2 = job.poll(listener);
		assertEquals(PollingResult.NO_CHANGES, poll2);
	}

	@Test
	public void testPatternListInvalidPattern() throws Exception {

		String client = "PatternListInvalidPattern.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternListInvalidPattern");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new AutoCleanImpl();
		List<Filter> filter = new ArrayList<>();

		// Constuct a correct regex for File-0.txt, but test a broken regex for .dat files
		String sb = "//depot/main/[a-z]+-0\\.txt" +
				"\n" +
				"{a-z\\}+\\.dat"; // Should be using []'s, not {}'s!

		FilterPatternListImpl pList = new FilterPatternListImpl(sb, false);
		filter.add(pList);

		// Should only be one actual regex generated
		assertEquals(1, pList.getPatternList().size());

		/* Should result in the follow changes captured:
		 * 8: 0
		 * 14: 0
		 * -17-: .dat files are added, but we have a broken regex!
		 * -18-: //depot/Data/file-1.dat, but we have a broken regex!
		 * Total: 2 changes
		 */
		long[] changesExpected = {14L, 8L};

		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);

		// Test all expected changes
		for (long c : changesExpected) {
			assertThat(pollHandler.getLogBuffer(), containsString("found change: " + c));
		}
	}

	@Test
	public void shouldNotTriggerJobIfNoChange() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("NotTriggerJobIfNoChange");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Run once
		jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause()));

		// Test trigger
		trigger.poke(project, p4d.getRshPort());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		int lastBuildNumber = Objects.requireNonNull(project.getLastBuild()).getNumber();
		assertEquals("Shouldn't have triggered a build if no change", 1, lastBuildNumber);
	}

	@Test
	public void shouldTriggerJobIfChanges() throws Exception {
		String client = "TriggerJobIfChanges.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		FreeStyleProject project = jenkins.createFreeStyleProject("TriggerJobIfChanges");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);
		// Pin at label auto15
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Checkout at commit 9
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		// Run once
		Run<FreeStyleProject, FreeStyleBuild> lastRun = jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause(), actions));
		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(project, p4d.getRshPort());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		int lastBuildNumber = Objects.requireNonNull(project.getLastBuild()).getNumber();
		assertEquals("Should have triggered a build on change", 2, lastBuildNumber);
	}

	@Test
	public void testShouldTriggerPipelineJobIfChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "TriggerPipelineJobIfChanges");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "',"
				+ "      depotPath: '//depot/...', format: 'test.ws'\n"
				+ "}", false));

		// Set review to build change 9
		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		QueueTaskFuture<WorkflowRun> workFlowRunFuture = Objects.requireNonNull(job.scheduleBuild2(0, actions));
		WorkflowRun run = workFlowRunFuture.get();
		jenkins.assertBuildStatusSuccess(run);
		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", run);

		// Test trigger
		trigger.poke(job, p4d.getRshPort());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on changes", 2, job.getLastBuild().getNumber());
	}

	@Test
	public void testShouldNotTriggerPipelineIfNoChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "NotTriggerPipelineIfNoChanges");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'" + "\n"
				+ "}", false));

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		WorkflowRun lastRun = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(job, p4d.getRshPort());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Shouldn't have triggered a build as no changes", 1, job.getLastBuild().getNumber());
	}

	@Test
	public void testPipelinePollingInc() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PipelinePollingInc");
		job.setDefinition(new CpsFlowDefinition("node {\n"
				+ "checkout perforce(\n"
				+ "  credential: '" + CREDENTIAL + "', \n"
				+ "  filter: [incremental(true)], \n"
				+ "  populate: forceClean(have: false, pin: '', quiet: true), \n"
				+ "  workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "    pinHost: false, \n"
				+ "    spec: clientSpec(view: '//depot/... //${P4_CLIENT}/...')))\n"
				+ "}", false));

		WorkflowRun run1 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run1);

		// Change 1
		String c1 = submitFile(jenkins, "//depot/main/inc.001", "content");

		// Change 2
		String c2 = submitFile(jenkins, "//depot/main/inc.002", "content");

		// add handler to read polling log
		Logger polling = Logger.getLogger("IncPolling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);

		// poll for changes
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		job.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found change: " + c1));
		assertThat(pollHandler.getLogBuffer(), containsString("found change: " + c2));

		WorkflowRun run2 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change: " + c1, run2);

		// Change 3
		submitFile(jenkins, "//depot/main/inc.003", "content");

		WorkflowRun run3 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change: " + c2, run3);
	}

	@Test
	public void testPollingBuildInProgress() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PollingBuildInProgress");
		job.setDefinition(new CpsFlowDefinition(""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        checkout perforce(\n"
				+ "          credential: '" + CREDENTIAL + "', \n"
				+ "          populate: forceClean(quiet: true),\n"
				+ "          workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "            spec: clientSpec(view: '//depot/main/... //${P4_CLIENT}/...')))\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}", false));

		// enable SCM polling (even though we poll programmatically)
		SCMTrigger cron = new SCMTrigger("* * * * *");
		job.addTrigger(cron);

		// disable concurrent builds
		job.setConcurrentBuild(false);
		job.setQuietPeriod(1);

		// Base-line build
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// Submit first change
		submitFile(jenkins, "//depot/main/file.001", "content");

		// Poll for changes multiple times
		for (int i = 0; i < 300; i++) {
			Thread.sleep(40);
			cron.run();
		}
		jenkins.waitUntilNoActivity();

		assertEquals("Poll and trigger Build #2", 2, job.getLastBuild().number);
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
	}

	@Test
	public void testPollingLatestChangeFilter() throws Exception {
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PollingLatestChangeFilter");
		job.setDefinition(new CpsFlowDefinition(""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        sleep 4\n"
				+ "        checkout perforce(\n"
				+ "          credential: '" + CREDENTIAL + "', \n"
				+ "          filter: [latest(true)], \n"
				+ "          populate: forceClean(quiet: true),\n"
				+ "          workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "            spec: clientSpec(view: '//depot/main/... //${P4_CLIENT}/...')))\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}", false));

		// enable SCM polling (even though we poll programmatically)
		SCMTrigger cron = new SCMTrigger("* * * * *");
		job.addTrigger(cron);

		// disable concurrent builds
		job.setConcurrentBuild(false);

		// Base-line build
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// Submit first change
		String c1 = submitFile(jenkins, "//depot/main/file.001", "content");

		Thread.sleep(60);
		// Poll for changes
		cron.run();

		// Submit additional change
		submitFile(jenkins, "//depot/main/file.002", "content");
		submitFile(jenkins, "//depot/main/file.003", "content");
		jenkins.waitUntilNoActivity();

		WorkflowRun run = job.getLastBuild();
		assertEquals("Poll and trigger Build #2", 2, run.number);
		assertEquals(Result.SUCCESS, run.getResult());
		jenkins.assertLogContains("P4 Task: syncing files at change: " + c1, run);
		jenkins.assertLogContains("Baseline: " + c1, run);
	}

	@Test
	public void testPipelineFailedPolling() throws Exception {

		String pass = """
				\
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        echo 'test'
				      }
				    }
				  }
				}""";

		String fail = """
				\
				pipeline {
				  agentxxx
				  }
				}""";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", pass, "Pass Jenkinsfile");

		// Manual workspace spec definition
		String client = "basicJenkinsfile.ws";
		String view = "//depot/Data/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate and Filter options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, null, populate, null);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PipelineFailedPolling");
		job.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

		// Set lightweight checkout
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		// enable SCM polling (even though we poll programmatically)
		SCMTrigger cron = new SCMTrigger("0 0 * * *");
		job.addTrigger(cron);

		// Build #1
		WorkflowRun build = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", build);

		// Poll for changes incrementally (change 1)
		submitFile(jenkins, "//depot/Data/Jenkinsfile", fail, "Fail Jenkinsfile");
		cron.run();
		waitForBuild(job, 2);
		jenkins.waitUntilNoActivity();
		assertEquals("Poll and trigger Build #2", 2, job.getLastBuild().number);
		assertEquals(Result.FAILURE, job.getLastBuild().getResult());

		// Poll for changes incrementally (no change)
		cron.run();
		Thread.sleep(500);
		jenkins.waitUntilNoActivity();
		assertEquals("Poll, but no build", 2, job.getLastBuild().number);
	}

	@Test
	public void testMultiBranchFailedPolling() throws Exception {

		// Setup sample Multi Branch Project
		String branch = "Main";
		String jfile = "Jenkinsfile";
		String base = "//depot/multiFailPoll";
		String baseChange = submitFile(jenkins, base + "/" + branch + "/" + jfile, ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "          if(!fileExists('" + jfile + "')) error 'missing " + jfile + "'\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");
		assertNotNull(baseChange);

		// Setup Branch Source
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		// Setup MultiBranch Job
		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "MultiBranchFailedPolling");
		multi.getSourcesList().add(new BranchSource(source));
		TimerTrigger timer = new TimerTrigger("0 0 * * *");
		multi.addTrigger(timer);

		// Build #1
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Add bad Jenkinsfile
		submitFile(jenkins, base + "/" + branch + "/" + jfile, """
				\
				pipeline {
				  agentxxx
				}""");

		// Poll - Build #2 (fail)
		timer.run();
		Thread.sleep(500);
		jenkins.waitUntilNoActivity();
		assertEquals(2, job.getLastBuild().number);
		assertEquals(Result.FAILURE, job.getLastBuild().getResult());

		// Poll - No build
		timer.run();
		Thread.sleep(500);
		jenkins.waitUntilNoActivity();
		assertEquals(2, job.getLastBuild().number);
	}

	@Test
	public void testTemplateWorkspacePollingShouldNotSetClientRootNull() throws Exception {
		String client = "test.ws";
		String format = "jenkins-${JOB_NAME}.ws";

		FreeStyleProject project = jenkins.createFreeStyleProject("Jenkins59300");
		TemplateWorkspaceImpl workspace = new TemplateWorkspaceImpl("none", true, client, format);

		EnvVars envVars = new EnvVars();
		envVars.put("JOB_NAME", "Jenkins59300");
		workspace.setExpand(envVars);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());

		String workspaceExpandedName = workspace.getFullName();


		try (ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null)) {
			// Connect to p4d server
			IOptionsServer p4Connection = p4.getConnection();
			// Get workspace root on p4d before polling
			IClient p4Client = p4Connection.getClient(workspaceExpandedName);
			String workspaceRootBeforePoll = p4Client.getRoot();

			// Submit a file
			submitFile(jenkins, "//depot/test59300", "content");

			// Trigger Polling
			Logger polling = Logger.getLogger("IncPolling");
			TestHandler pollHandler = new TestHandler();
			polling.addHandler(pollHandler);
			LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
			project.poll(listener);


			// Get workspace root on p4d after polling
			p4Client = p4Connection.getClient(workspaceExpandedName);
			String workspaceRootAfterPoll = p4Client.getRoot();

			assertEquals(workspaceRootBeforePoll, workspaceRootAfterPoll);
		}
	}

	@Test
	public void pollingShouldPollToLatestWithPin() throws Exception {
		// Submit a change. Create a job and pin to this changelist. latestWithPin = true
		String pinChangelist = submitFile(jenkins, "//depot/main/incPoll.001", "content");
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PollLatestWithPin");
		job.setDefinition(new CpsFlowDefinition("node {\n"
				+ "checkout perforce(\n"
				+ "  credential: '" + CREDENTIAL + "', \n"
				+ "  filter: [latestWithPin(true)], \n"
				+ "  populate: forceClean(have: false, pin: '" + pinChangelist + "', quiet: true), \n"
				+ "  workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "    pinHost: false, \n"
				+ "    spec: clientSpec(view: '//depot/... //${P4_CLIENT}/...')))\n"
				+ "}", false));

		WorkflowRun build1 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", build1);

		// Submit new change
		String newChange = submitFile(jenkins, "//depot/main/inc.002", "content");

		// Add polling log listener and poll
		Logger polling = Logger.getLogger("PollLatestWithPin");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		job.poll(listener);

		// As latestWithPin is true, polling should poll till new change even checkout is pinned to old change
		assertThat(pollHandler.getLogBuffer(), containsString("P4: Polling found change: " + newChange));
	}

	@Test
	public void pollingShouldNotPollToLatestWithPin() throws Exception {
		// Submit a change. Create a job and pin to this changelist. latestWithPin = false
		String pinChangelist = submitFile(jenkins, "//depot/main/incPoll.001", "content");
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PollPinnedChange");
		job.setDefinition(new CpsFlowDefinition("node {\n"
				+ "checkout perforce(\n"
				+ "  credential: '" + CREDENTIAL + "', \n"
				+ "  filter: [latestWithPin(false)], \n"
				+ "  populate: forceClean(have: false, pin: '" + pinChangelist + "', quiet: true), \n"
				+ "  workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "    pinHost: false, \n"
				+ "    spec: clientSpec(view: '//depot/... //${P4_CLIENT}/...')))\n"
				+ "}", false));

		WorkflowRun build1 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", build1);

		// Submit new change
		String newChange = submitFile(jenkins, "//depot/main/inc.002", "content");

		// Add polling log listener and poll
		Logger polling = Logger.getLogger("IncPolling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		job.poll(listener);

		// latestWithPin is false, existing behaviour polling should restrict to pinned change
		assertThat(pollHandler.getLogBuffer(), containsString("Found last change " + pinChangelist));
		assertThat(pollHandler.getLogBuffer(), containsString("P4: Polling no changes found."));
		assertThat(pollHandler.getLogBuffer(), not(containsString("P4: Polling found change: " + newChange)));
	}

	@Test
	public void pollToLatestWithMultipleCheckout() throws Exception {
		// Submit a change. Create a job with multiple checkout steps
		String pinChangelist = submitFile(jenkins, "//depot/main/incPoll.001", "content");
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "PollWithMultiCheckout");
		job.setDefinition(new CpsFlowDefinition("node {\n"
				+ "checkout perforce(\n"
				+ "  credential: '" + CREDENTIAL + "', \n"
				+ "  filter: [latestWithPin(true)], \n"
				+ "  populate: forceClean(have: false, pin: '" + pinChangelist + "', quiet: true), \n"
				+ "  workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "    pinHost: false, \n"
				+ "    spec: clientSpec(view: '//depot/... //${P4_CLIENT}/...')))\n"
				+ "checkout perforce(\n"
				+ "  credential: '" + CREDENTIAL + "', \n"
				+ "  filter: [latestWithPin(false)], \n"
				+ "  populate: forceClean(have: false, pin: '" + pinChangelist + "', quiet: true), \n"
				+ "  workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', \n"
				+ "    pinHost: false, \n"
				+ "    spec: clientSpec(view: '//depot/... //${P4_CLIENT}/...')))\n"
				+ "}", false));

		WorkflowRun build1 = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", build1);

		// Submit new change
		String newChange = submitFile(jenkins, "//depot/main/inc.002", "content");

		// Add polling log listener and poll
		Logger polling = Logger.getLogger("IncPolling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		job.poll(listener);

		assertThat(pollHandler.getLogBuffer(), containsString("P4: Polling with range: "+pinChangelist+",now"));
		assertThat(pollHandler.getLogBuffer(), containsString("P4: Polling with range: "+pinChangelist+","+pinChangelist));
		assertThat(pollHandler.getLogBuffer(), containsString("P4: Polling found change: " + newChange));
	}
}
