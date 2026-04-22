package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import hudson.EnvVars;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class WorkspaceSpecTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-WorkspaceSpecTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r17);

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
    }

    @BeforeEach
    void beforeEach() throws Exception {
		createCredentials("jenkins", "Password", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testChangeViewFreeStyle() throws Exception {
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}.ws";
		String view = "//depot/Jam/... //" + format + "/...";
		String cview = "//depot/Jam/...@10099";
		WorkspaceSpec spec = new WorkspaceSpec(view, cview);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, format, spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		FreeStyleProject project = jenkins.createFreeStyleProject("ChangeView");
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// Test changeView is applied and source is at change 10099
		jenkins.assertLogContains("Change 10099 on 2002/01/21 by rmg@rmg:pdjam:chinacat 'This change is integration hist'", build);
	}

	@Test
	void testBackupFalseFreeStyle() throws Exception {
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}.ws";
		String view = "//depot/Jam/... //" + format + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(false, true, false, false, false, false, null, "LOCAL", view, null, null, null, false);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, format, spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		FreeStyleProject project = jenkins.createFreeStyleProject("ClientBackup");
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		// create pseudo environment (normally ClientHelper is called from Run)
		EnvVars envVars = new EnvVars();
		envVars.put("NODE_NAME", "NODE_NAME");
		envVars.put("JOB_NAME", "JOB_NAME");
		workspace.setExpand(envVars);

		// Log in for next set of tests...
		ClientHelper p4 = new ClientHelper(project, CREDENTIAL, null, workspace);
		p4.login();

		// Test backup field in client spec
		IClient iclient = p4.getConnection().getCurrentClient();
		assertNotNull(iclient);
		assertEquals("disable", iclient.getBackup());
		p4.disconnect();
	}

	/**
	 * test for https://issues.jenkins.io/browse/JENKINS-69491
	 */
	@Test
	void adjustViewLineTest() {
		String clientName = "CLIENT";
		String view = "\n//depot/Jam/... //placeholder/...\n" +
				"//depot/java/yo/...\n\n" +
				"-//depot/java/stuff //otherStuff/java/stuff\n\n" +
				"//products/no/where/rhs\n" ;
		String expectedView = "//depot/Jam/... //CLIENT/...\n" +
				"//depot/java/yo/... //CLIENT/java/yo/...\n" +
				"-//depot/java/stuff //CLIENT/java/stuff\n" +
				"//products/no/where/rhs //CLIENT/no/where/rhs" ;

		WorkspaceSpec spec = new WorkspaceSpec(false, true, false, false, false, false, null, "LOCAL", view, null, null, null, false);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, clientName, spec,false);

		StringBuilder postView = new StringBuilder(300);
		for (String line : view.split("\n\\s*")) {
			if ( postView.length() > 0) {
				postView.append("\n");
			}
			postView.append( workspace.adjustViewLine(line, clientName, true));

		}
		System.out.println(p4d.getRshPort());
		assertEquals(expectedView, postView.toString());
	}

}
