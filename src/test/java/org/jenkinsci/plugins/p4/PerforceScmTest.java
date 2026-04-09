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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class PerforceScmTest extends DefaultEnvironment {

	private JenkinsRule jenkins;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        jenkins = rule;
    }

	@Test
	void testConfigBasic() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject();

		String credential = "123";
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
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
	void testIsBuildParent() throws IOException {
		MatrixProject project = new MatrixProject("MatrixTest");

		String credential = "123";
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);

		project.setExecutionStrategy(new DefaultMatrixExecutionStrategyImpl());
		assertFalse(scm.isBuildParent(project),
				"isBuildParent should be false for default execution strategy");

		project.setExecutionStrategy(new MatrixOptions(true, false, false));
		assertTrue(scm.isBuildParent(project),
				"isBuildParent should be true when MatrixOptions#buildParent is true");

		project.setExecutionStrategy(new MatrixOptions(false, true, true));
		assertFalse(scm.isBuildParent(project),
				"isBuildParent should be false when MatrixOptions#buildParent is false");
	}

}
