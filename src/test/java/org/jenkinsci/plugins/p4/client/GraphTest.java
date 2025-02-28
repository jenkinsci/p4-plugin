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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraphTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(GraphTest.class.getName());
	private static final String P4ROOT = "tmp-GraphTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R17_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "Password", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testFreeStyleForceSync() throws Exception {

		String client = "graph.ws";
		String view = "//depot/jam/... //" + client + "/jam/...\n";
		view += "//graph/scm-api-plugin/... //" + client + "/scm-api/...\n";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("FreeGraph");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new GraphHybridImpl(false, "10279", null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		List<ParameterValue> list = new ArrayList<>();
		list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "committed"));
		list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), "10279"));
		Action actions = new SafeParametersAction(new ArrayList<>(), list);

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
		String view = "//depot/jam/... //" + client + "/jam/...\n";
		view += "//graph/scm-api-plugin/... //" + client + "/scm-api/...\n";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);

		FreeStyleProject project = jenkins.createFreeStyleProject("FreeGraphPolling");
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		Populate populate = new GraphHybridImpl(false, null, null);
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		Action actions = new SafeParametersAction(new ArrayList<>(), new ArrayList<>());

		// Build at latest
		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause, actions).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Submit graph add
		commitFile(jenkins, "//graph/scm-api-plugin/test.add", "Content");

		// Poll for changes
		Logger polling = Logger.getLogger("Polling");
		TestHandler pollHandler = new TestHandler();
		polling.addHandler(pollHandler);
		LogTaskListener listener = new LogTaskListener(polling, Level.INFO);
		project.poll(listener);
		assertThat(pollHandler.getLogBuffer(), containsString("found commit: //graph/scm-api-plugin.git"));
	}
}
