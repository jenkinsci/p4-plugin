package org.jenkinsci.plugins.p4_client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import hudson.model.FreeStyleProject;
import hudson.scm.SCM;

import org.jenkinsci.plugins.p4_client.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4_client.populate.Populate;
import org.jenkinsci.plugins.p4_client.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4_client.workspace.Workspace;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PerforceScmTest {

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Test
	public void testConfigBasic() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject();

		String credential = "123";
		Workspace workspace = new StaticWorkspaceImpl("none", "test.ws");
		Populate populate = new AutoCleanImpl(true, true, null);
		PerforceScm scm = new PerforceScm(credential, workspace, populate);

		project.setScm(scm);
		SCM testScm = project.getScm();
		assertEquals("org.jenkinsci.plugins.p4_client.PerforceScm",
				testScm.getType());

		assertTrue(testScm.supportsPolling());
		assertTrue(testScm.requiresWorkspaceForPolling());

		assertEquals(testScm, project.getScm());
	}

}
