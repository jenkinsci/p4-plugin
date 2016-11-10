package org.jenkinsci.plugins.p4;

import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import hudson.scm.SCM;
import org.jenkinsci.plugins.p4.matrix.MatrixOptions;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PerforceScmTest {

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Test
	public void testConfigBasic() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject();

		String credential = "123";
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);

		project.setScm(scm);
		SCM testScm = project.getScm();
		assertEquals("org.jenkinsci.plugins.p4.PerforceScm", testScm.getType());

		assertTrue(testScm.supportsPolling());
		assertFalse(testScm.requiresWorkspaceForPolling());

		assertEquals(testScm, project.getScm());
	}

	@Test
	public void testIsBuildParent() throws IOException {
		MatrixProject project = new MatrixProject("MatrixTest");

		String credential = "123";
		Workspace workspace = new StaticWorkspaceImpl("none", false, "test.ws");
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);

		project.setExecutionStrategy(new DefaultMatrixExecutionStrategyImpl());
		assertFalse("isBuildParent should be false for default execution strategy",
				scm.isBuildParent(project));

		project.setExecutionStrategy(new MatrixOptions(true, false, false));
		assertTrue("isBuildParent should be true when MatrixOptions#buildParent is true",
				scm.isBuildParent(project));

		project.setExecutionStrategy(new MatrixOptions(false, true, true));
		assertFalse("isBuildParent should be false when MatrixOptions#buildParent is false",
				scm.isBuildParent(project));
	}

}
