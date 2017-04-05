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
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PollingTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(PollingTest.class.getName());
	private static final String P4ROOT = "tmp-PollingTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
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
		List<P4Revision> buildList = scm.getIncrementalChanges();
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
		List<P4Revision> buildList = scm.getIncrementalChanges();
		assertEquals(2, buildList.size());
		int change = buildList.get(0).getChange();
		assertEquals(18, change);
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
		List<P4Revision> buildList = scm.getIncrementalChanges();
		assertEquals(13, buildList.size());
		int change = buildList.get(0).getChange();
		assertEquals(16, change);
	}

	@Test
	public void shouldNotTriggerJobIfNoChange() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("NotTriggerJobIfNoChange");
		StaticWorkspaceImpl workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl(true, true, false, false, null, null);
		PerforceScm scm = new PerforceScm(auth.getId(), workspace, populate);
		project.setScm(scm);
		P4Trigger trigger = new P4Trigger();
		trigger.start(project, false);
		project.addTrigger(trigger);
		project.save();

		// Run once
		jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause()));

		// Test trigger
		trigger.poke(project, auth.getP4port());

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
		PerforceScm scm = new PerforceScm(auth.getId(), workspace, populate);
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
		trigger.poke(project, auth.getP4port());

		TimeUnit.SECONDS.sleep(project.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on change", 2, project.getLastBuild().getNumber());
	}

	@Test
	public void testShouldTriggerPipelineJobIfChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "TriggerPipelineJobIfChanges");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "   p4sync credential: '" + auth.getId() + "',"
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
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Should have triggered a build on changes", 2, job.getLastBuild().getNumber());
	}

	@Test
	public void testShouldNotTriggerPipelineIfNoChanges() throws Exception {

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "NotTriggerPipelineIfNoChanges");
		job.setDefinition(new CpsFlowDefinition(
				"" + "node {\n" + "   p4sync credential: '" + auth.getId() + "', template: 'test.ws'" + "\n" + "}"));

		// Add a trigger
		P4Trigger trigger = new P4Trigger();
		trigger.start(job, false);
		job.addTrigger(trigger);
		job.save();

		WorkflowRun lastRun = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkins.waitUntilNoActivity();
		jenkins.assertLogContains("P4 Task: syncing files at change", lastRun);

		// Test trigger
		trigger.poke(job, auth.getP4port());

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertEquals("Shouldn't have triggered a build as no changes", 1, job.getLastBuild().getNumber());
	}
}
