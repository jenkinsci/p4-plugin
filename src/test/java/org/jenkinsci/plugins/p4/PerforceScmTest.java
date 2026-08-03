package org.jenkinsci.plugins.p4;

import hudson.matrix.DefaultMatrixExecutionStrategyImpl;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import hudson.scm.SCM;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.matrix.MatrixOptions;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

	@Test
	void testConfigureParsesAllFieldsOnHappyPath() {
		PerforceScm.DescriptorImpl descriptor = jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);

		Map<String, Object> data = new HashMap<>();
		data.put("autoSave", true);
		data.put("credential", "cred-1");
		data.put("clientName", "client-1");
		data.put("depotPath", "//depot/...");
		data.put("autoSubmitOnChange", true);
		data.put("deleteClient", true);
		data.put("deleteFiles", true);
		data.put("hideTicket", true);
		data.put("maxFiles", 100);
		data.put("maxChanges", 75);
		data.put("headLimit", 12345L);
		data.put("lastSuccess", true);
		data.put("hideMessages", true);
		data.put("recursionInPolling", true);

		assertTrue(descriptor.configure((StaplerRequest2) null, JSONObject.fromObject(data)));

		assertTrue(descriptor.isAutoSave());
		assertEquals("cred-1", descriptor.getCredential());
		assertEquals("client-1", descriptor.getClientName());
		assertEquals("//depot/...", descriptor.getDepotPath());
		assertTrue(descriptor.isAutoSubmitOnChange());
		assertTrue(descriptor.isDeleteClient());
		assertTrue(descriptor.isDeleteFiles());
		assertTrue(descriptor.isHideTicket());
		assertEquals(100, descriptor.getMaxFiles());
		assertEquals(75, descriptor.getMaxChanges());
		assertEquals(12345L, descriptor.getHeadLimit());
		assertTrue(descriptor.isLastSuccess());
		assertTrue(descriptor.isHideMessages());
		assertTrue(descriptor.isRecursionInPolling());
	}

	@Test
	void testConfigureFallsBackToDefaultsWhenJsonFieldsAreMissing() {
		PerforceScm.DescriptorImpl descriptor = jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);

		assertTrue(descriptor.configure((StaplerRequest2) null, new JSONObject()));

		assertFalse(descriptor.isAutoSave());
		assertFalse(descriptor.isDeleteClient());
		assertFalse(descriptor.isDeleteFiles());
		assertFalse(descriptor.isHideTicket());
		assertEquals(PerforceScm.DEFAULT_FILE_LIMIT, descriptor.getMaxFiles());
		assertEquals(PerforceScm.DEFAULT_CHANGE_LIMIT, descriptor.getMaxChanges());
		assertEquals(PerforceScm.DEFAULT_HEAD_LIMIT, descriptor.getHeadLimit());
		assertFalse(descriptor.isLastSuccess());
		assertFalse(descriptor.isHideMessages());
		assertFalse(descriptor.isRecursionInPolling());
	}

	@Test
	void testConfigurePartialGroupFailureResetsAutoSaveEvenThoughItParsedFirst() {
		PerforceScm.DescriptorImpl descriptor = jenkins.jenkins.getDescriptorByType(PerforceScm.DescriptorImpl.class);

		Map<String, Object> data = new HashMap<>();
		data.put("autoSave", true);
		data.put("credential", "cred-1");
		// clientName intentionally omitted: json.getString("clientName") throws JSONException
		// partway through the group, after autoSave has already been read as true.
		data.put("depotPath", "//depot/...");
		data.put("autoSubmitOnChange", true);

		assertTrue(descriptor.configure((StaplerRequest2) null, JSONObject.fromObject(data)));

		assertFalse(descriptor.isAutoSave(),
				"autoSave should be reset to false when a later field in the same group fails to parse");
	}

}
