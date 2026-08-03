package org.jenkinsci.plugins.p4.unit.workspace;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class ManualWorkspaceImplTest {

	private ManualWorkspaceImpl newWorkspace() {
		WorkspaceSpec spec = new WorkspaceSpec("//depot/main/...", null);
		return new ManualWorkspaceImpl("none", false, "myclient", spec, false);
	}

	@Test
	void testAdjustViewLineBuildsRhsForDepotOnlyLine() {
		ManualWorkspaceImpl workspace = newWorkspace();

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			Jenkins jenkins = mock(Jenkins.class);
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			String result = workspace.adjustViewLine("//depot/path/to/product/...", "myclient", false);

			assertEquals("//depot/path/to/product/... //myclient/path/to/product/...", result);
		}
	}

	@Test
	void testAdjustViewLineReplacesClientNameWhenRequested() {
		ManualWorkspaceImpl workspace = newWorkspace();

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			Jenkins jenkins = mock(Jenkins.class);
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			String result = workspace.adjustViewLine("//depot/path/... //oldclient/path/...", "myclient", true);

			assertEquals("//depot/path/... //myclient/path/...", result);
		}
	}

	@Test
	void testAdjustViewLineLeavesLineUnchangedWhenNotAdjustingClientName() {
		ManualWorkspaceImpl workspace = newWorkspace();

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			Jenkins jenkins = mock(Jenkins.class);
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			String line = "//depot/path/... //myclient/path/...";
			String result = workspace.adjustViewLine(line, "myclient", false);

			assertEquals(line, result);
		}
	}

	@Test
	void testAdjustViewLineLeavesMalformedLineUnchanged() {
		ManualWorkspaceImpl workspace = newWorkspace();

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			Jenkins jenkins = mock(Jenkins.class);
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

			String result = workspace.adjustViewLine("no depot path here", "myclient", true);

			assertEquals("no depot path here", result);
		}
	}
}
