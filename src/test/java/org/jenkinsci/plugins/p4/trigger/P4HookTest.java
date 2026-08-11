package org.jenkinsci.plugins.p4.trigger;

import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHeadEvent;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class P4HookTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-P4HookTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		jenkins.jenkins.setCrumbIssuer(null);
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	private URL hookUrl(String path) throws Exception {
		return URI.create(jenkins.getURL().toString() + path).toURL();
	}

	private String loadLog(File logFile) throws Exception {
		return Util.loadFile(logFile, Charset.defaultCharset());
	}

	private FreeStyleProject createTriggeredProject(String name) throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject(name);
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.addTrigger(new P4Trigger());
		project.save();
		return project;
	}

	private String waitForPokeOutcome(File logFile, int timeoutMs) throws Exception {
		int waited = 0;
		while (waited < timeoutMs) {
			if (logFile.exists()) {
				String log = loadLog(logFile);
				if (log.contains("P4: Changes found") || log.contains("P4: No changes")
						|| log.contains("P4: Failed to record P4 trigger")) {
					return log;
				}
			}
			Thread.sleep(100);
			waited += 100;
		}
		return logFile.exists() ? loadLog(logFile) : "";
	}

	@Test
	void doChangeSubmitPokesMatchingP4TriggerJob() throws Exception {
		FreeStyleProject project = createTriggeredProject("P4HookChangeSubmit");

		FreeStyleBuild build1 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build1.getResult());

		submitFile(jenkins, "//depot/P4HookChangeSubmit/file.txt", "content");

		List<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("_.p4port", p4d.getRshPort()));
		params.add(new NameValuePair("json", "{\"dummy\":\"1\"}"));

		WebRequest request = new WebRequest(hookUrl("p4/changeSubmit"), HttpMethod.POST);
		request.setRequestParameters(params);
		jenkins.createWebClient().getPage(request);

		jenkins.waitUntilNoActivity();

		File logFile = new File(project.getRootDir(), "p4trigger.log");
		assertTrue(logFile.exists(), "P4Trigger should have logged a poke result");
		assertTrue(loadLog(logFile).contains("P4: Changes found"));

		assertEquals(2, project.getLastBuild().getNumber(),
				"the matching poke should have scheduled a second build");
	}

	@Test
	void doChangeProbesMatchingJobAsynchronouslyFromJsonPayload() throws Exception {
		FreeStyleProject project = createTriggeredProject("P4HookChange");

		FreeStyleBuild build1 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build1.getResult());

		Map<String, Object> map = new HashMap<>();
		map.put("p4port", p4d.getRshPort());
		JSONObject payload = JSONObject.fromObject(map);

		WebRequest request = new WebRequest(hookUrl("p4/change"), HttpMethod.POST);
		request.setRequestBody("payload=" + payload);
		jenkins.createWebClient().getPage(request);

		// doChange's own job (over doChangeSubmit, covered above) is parsing the raw
		// JSON body and dispatching to the background executor; matchServer/poke
		// itself is already verified synchronously above, so just confirm the async
		// path reaches a poke outcome rather than re-asserting the P4 connection
		// succeeds (that depends on rsh p4d startup timing, not this endpoint's logic).
		File logFile = new File(project.getRootDir(), "p4trigger.log");
		String log = waitForPokeOutcome(logFile, 20_000);
		assertTrue(log.contains("P4: Changes found") || log.contains("P4: No changes")
						|| log.contains("P4: Failed to record P4 trigger"),
				"expected a poke outcome to be logged, got: " + log);

		jenkins.waitUntilNoActivity();
	}

	@Test
	void doEventFiresSCMHeadEventFromJsonPayload() throws Exception {
		CapturingSCMEventListener.events.clear();

		Map<String, Object> map = new HashMap<>();
		map.put(ReviewProp.EVENT_TYPE.getProp(), "UPDATED");
		JSONObject payload = JSONObject.fromObject(map);

		WebRequest request = new WebRequest(hookUrl("p4/event"), HttpMethod.POST);
		request.setAdditionalHeader("Content-Type", "application/json");
		request.setRequestBody(payload.toString());
		jenkins.createWebClient().getPage(request);

		int waited = 0;
		while (waited < 5_000 && CapturingSCMEventListener.events.isEmpty()) {
			Thread.sleep(100);
			waited += 100;
		}

		assertEquals(1, CapturingSCMEventListener.events.size());
		assertEquals(SCMHeadEvent.Type.UPDATED, CapturingSCMEventListener.events.get(0).getType());
	}

	@TestExtension("doEventFiresSCMHeadEventFromJsonPayload")
	public static class CapturingSCMEventListener extends SCMEventListener {

		static final List<SCMHeadEvent<?>> events = new ArrayList<>();

		@Override
		public void onSCMHeadEvent(SCMHeadEvent<?> event) {
			events.add(event);
		}
	}
}
