package org.jenkinsci.plugins.p4.client;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.scm.PollingResult;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPatternListImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterViewMaskImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.trigger.P4Trigger;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class PollingTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(PollingTest.class.getName());
	private static final String P4ROOT = "tmp-PollingTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testPollingPin() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingPin");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, "auto15", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(12, buildList.size());
	}

	@Test
	public void testPollingInc() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingInc");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, "auto15", null);
		List<Filter> filter = new ArrayList<Filter>();
		FilterPerChangeImpl inc = new FilterPerChangeImpl(true);
		filter.add(inc);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		PollingResult found = project.poll(listener);
		assertEquals(PollingResult.BUILD_NOW, found);

		// Build now
		build = project.scheduleBuild2(0, cause).get();
		List<String> log = build.getLog(LOG_LIMIT);
		assertTrue(log.contains("P4 Task: syncing files at change: 4"));
	}

	@Test
	public void testPollingMask() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingMask");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		// Filter changes outside of //depot/Data path
		FilterViewMaskImpl mask = new FilterViewMaskImpl("//depot/Data");
		filter.add(mask);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(2, buildList.size());
		long change = buildList.get(0).getChange();
		assertEquals(18L, change);
	}

	@Test
	public void testPollingMaskExcl() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PollingMaskExcl");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		// Filter changes outside of //depot/Main and also under //depot/Main/TPI-83
		StringBuilder sb = new StringBuilder();
		sb.append("//depot/Main");
		sb.append("\n");
		sb.append("-//depot/Main/TPI-83");

		FilterViewMaskImpl mask = new FilterViewMaskImpl(sb.toString());
		filter.add(mask);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// TODO: determine the CL our mask should poll us at
		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(13, buildList.size());
		long change = buildList.get(0).getChange();
		assertEquals(16L, change);
	}

	@Test
	public void testPatternList() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternList");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		StringBuilder sb = new StringBuilder();

		// Only poll on 0 && .dat files's
		sb.append("//depot/main/[a-z]+-0\\.txt");
		sb.append("\n");
		sb.append(".*\\.dat$");

		FilterPatternListImpl pList = new FilterPatternListImpl(sb.toString(), false);
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
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(4, buildList.size());

		// Test all the changes that came back!
		for (int i = 0; i < buildList.size(); i++) {
			assertEquals(changesExpected[i], buildList.get(i).getChange());
		}
	}

	@Test
	public void testPatternListCaseSensitive() throws Exception {
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternListCaseSensitive");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		// Only poll on a main with lower case 'm' (doesn't actually exist) -- ensure case sensitive is TRUE!
		FilterPatternListImpl pList = new FilterPatternListImpl("//depot/main/file-.*\\.txt", true);
		filter.add(pList);

		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, filter, populate, null);
		project.setScm(scm);
		project.save();

		// Build at change 3
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(0, buildList.size());
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

		assertThat(pollHandler.getLogBuffer().toString(), containsString("found change: 44"));

		// Build to clear last change
		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// Submit a change hidden from polling...
		submitFile(jenkins, "//depot/main/bar.002", "content");

		PollingResult poll2 = job.poll(listener);
		assertEquals(PollingResult.NO_CHANGES, poll2);
	}

	@Test
	public void testPatternListInvalidPattern() throws Exception {

		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("PatternListInvalidPattern");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		List<Filter> filter = new ArrayList<Filter>();

		StringBuilder sb = new StringBuilder();

		// Constuct a correct regex for File-0.txt, but test a broken regex for .dat files
		sb.append("//depot/main/[a-z]+-0\\.txt");
		sb.append("\n");
		sb.append("{a-z\\}+\\.dat"); // Should be using []'s, not {}'s!

		FilterPatternListImpl pList = new FilterPatternListImpl(sb.toString(), false);
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
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "submitted"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "3"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Poll for changes incrementally
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(2, buildList.size());

		// Test all the changes that came back!
		for (int i = 0; i < buildList.size(); i++) {
			assertEquals(changesExpected[i], buildList.get(i).getChange());
		}
	}

	@Test
	public void shouldNotTriggerJobIfNoChange() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("NotTriggerJobIfNoChange");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
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

		assertEquals("Shouldn't have triggered a build if no change", 1, project.getLastBuild().getNumber());
	}

	@Test
	public void shouldTriggerJobIfChanges() throws Exception {
		String client = "manual.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);
		FreeStyleProject project = jenkins.createFreeStyleProject("TriggerJobIfChanges");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
		// Pin at label auto15
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Checkout at commit 9
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		// Run once
		Run lastRun = jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause(), actions));
		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(project, p4d.getRshPort());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on change", 2, project.getLastBuild().getNumber());
	}

	@Test
	public void testShouldTriggerPipelineJobIfChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "TriggerPipelineJobIfChanges");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "',"
				+ "      depotPath: '//depot', format: 'test.ws'\n"
				+ "}"));

		// Set review to build change 9
		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "9"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		WorkflowRun run = job.scheduleBuild2(0, actions).get();
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
		job.setDefinition(new CpsFlowDefinition(
				"" + "node {\n" + "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'" + "\n" + "}"));

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


	private class TestHandler extends Handler {

		private StringBuffer sb = new StringBuffer();

		@Override
		public void publish(LogRecord record) {
			sb.append(record.getMessage());
			sb.append("\n");
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}

		public StringBuffer getLogBuffer() {
			return sb;
		}
	}
}
