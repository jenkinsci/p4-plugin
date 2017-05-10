package org.jenkinsci.plugins.p4.client;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4GraphRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.GraphHybridImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraphTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(GraphTest.class.getName());
	private static final String P4ROOT = "tmp-GraphTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R17_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "Password", p4d);
	}

	@Test
	public void testFreeStyleForceSync() throws Exception {

		String client = "graph.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/jam/... //" + client + "/jam/...\n";
		view += "//graph/scm-api-plugin/... //" + client + "/scm-api/...\n";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("FreeGraph");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new GraphHybridImpl(false, "10279", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<ParameterValue>();
		list.add(new StringParameterValue(ReviewProp.STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.CHANGE.toString(), "10279"));
		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), list);

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		List<String> log = build.getLog(LOG_LIMIT);
		assertNotNull(log);

		assertTrue(log.contains("P4 Task: syncing files at change: 10279"));
		assertTrue(log.contains("... totalFileCount 75"));
	}

	@Test
	public void testPollingPin() throws Exception {

		String client = "graph.ws";
		String stream = null;
		String line = "LOCAL";
		String view = "//depot/jam/... //" + client + "/jam/...\n";
		view += "//graph/scm-api-plugin/... //" + client + "/scm-api/...\n";
		WorkspaceSpec spec = new WorkspaceSpec(false, false, false, false, false, false, stream, line, view);

		FreeStyleProject project = jenkins.createFreeStyleProject("FreeGraphPolling");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);

		Populate populate = new GraphHybridImpl(false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		Action actions = new SafeParametersAction(new ArrayList<ParameterValue>(), new ArrayList<ParameterValue>());

		// Build at latest
		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Submit graph add
		commitFile(jenkins, "//graph/scm-api-plugin/test.add", "Content");

		// Poll for changes
		LogTaskListener listener = new LogTaskListener(logger, Level.INFO);
		project.poll(listener);
		List<P4Ref> buildList = scm.getIncrementalChanges();
		assertEquals(1, buildList.size());

		P4Ref ref = buildList.get(0);
		assertTrue(ref instanceof P4GraphRef);
		P4GraphRef commit = (P4GraphRef) ref;
		assertEquals("//graph/scm-api-plugin.git", commit.getRepo());
	}
}
