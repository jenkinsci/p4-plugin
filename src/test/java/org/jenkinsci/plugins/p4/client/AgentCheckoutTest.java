package org.jenkinsci.plugins.p4.client;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class AgentCheckoutTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-AgentCheckoutTest-p4root";

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
	void checkoutOnRealAgentJvm() throws Exception {
		DumbSlave agent = jenkins.createOnlineSlave();

		FreeStyleProject project = jenkins.createFreeStyleProject("OnAgent");
		project.setAssignedNode(agent);
		String view = "//depot/Data/... //${P4_CLIENT}/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl ws = new ManualWorkspaceImpl("none", true, "agent.ws", spec, false);
		project.setScm(new PerforceScm(CREDENTIAL, ws, new AutoCleanImpl()));
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}
}
