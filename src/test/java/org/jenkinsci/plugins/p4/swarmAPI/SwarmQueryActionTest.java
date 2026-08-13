package org.jenkinsci.plugins.p4.swarmAPI;

import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.jupiter.api.BeforeEach;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.MockedConstruction;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WithJenkins
class SwarmQueryActionTest {

	private JenkinsRule jenkins;

	@BeforeEach
	void beforeEach(JenkinsRule rule) {
		jenkins = rule;
	}

	@Test
	void basicRootActionMetadata() {
		SwarmQueryAction action = new SwarmQueryAction();
		assertNull(action.getIconFileName());
		assertNull(action.getDisplayName());
		assertEquals("swarm", action.getUrlName());
	}

	@Test
	void doDynamicIgnoresUnrecognizedPaths() throws Exception {
		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getRestOfPath()).thenReturn("/unknown");

		new SwarmQueryAction().doDynamic(req, rsp);

		verifyNoInteractions(rsp);
	}

	@Test
	void doDynamicProjectPathWritesSwarmProjectsAsJson() throws Exception {
		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getRestOfPath()).thenReturn("/project");
		when(req.getParameter("credential")).thenReturn("cred-1");

		StringWriter sw = new StringWriter();
		when(rsp.getWriter()).thenReturn(new PrintWriter(sw));

		try (MockedConstruction<ConnectionHelper> connectionMock = mockConstruction(ConnectionHelper.class);
			 MockedConstruction<SwarmHelper> swarmMock = mockConstruction(SwarmHelper.class, (mock, context) ->
					 when(mock.getProjects()).thenReturn(List.of("proj1", "proj2")))) {

			new SwarmQueryAction().doDynamic(req, rsp);

			assertEquals(1, connectionMock.constructed().size());
			assertEquals(1, swarmMock.constructed().size());
			assertEquals("[\"proj1\",\"proj2\"]", sw.toString());
		}
	}

	@Test
	void doDynamicCreatePathSchedulesNewMultibranchProject() throws Exception {
		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getRestOfPath()).thenReturn("/create");
		when(req.getParameter("credential")).thenReturn("cred-1");
		when(req.getParameter("project")).thenReturn("swarm-project-1");
		when(req.getParameter("name")).thenReturn(null);

		StringWriter sw = new StringWriter();
		when(rsp.getWriter()).thenReturn(new PrintWriter(sw));

		try (MockedConstruction<ConnectionHelper> connectionMock = mockConstruction(ConnectionHelper.class);
			 MockedConstruction<SwarmHelper> swarmMock = mockConstruction(SwarmHelper.class)) {

			new SwarmQueryAction().doDynamic(req, rsp);
		}

		verify(rsp).setStatus(SC_CREATED);
		verify(rsp).setContentType("application/json");
		assertEquals("{\"name\":\"swarm-project-1\"}", sw.toString());

		WorkflowMultiBranchProject created = jenkins.jenkins.getItemByFullName("swarm-project-1", WorkflowMultiBranchProject.class);
		assertNotNull(created, "doDynamic should have created the multibranch project via Jenkins.get().createProject");
	}

	@Test
	void doDynamicCreatePathReturnsBadRequestWhenNameAlreadyExists() throws Exception {
		jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "already-exists");

		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getRestOfPath()).thenReturn("/create");
		when(req.getParameter("credential")).thenReturn("cred-1");
		when(req.getParameter("project")).thenReturn("already-exists");
		when(req.getParameter("name")).thenReturn(null);

		StringWriter sw = new StringWriter();
		when(rsp.getWriter()).thenReturn(new PrintWriter(sw));

		try (MockedConstruction<ConnectionHelper> connectionMock = mockConstruction(ConnectionHelper.class);
			 MockedConstruction<SwarmHelper> swarmMock = mockConstruction(SwarmHelper.class)) {

			new SwarmQueryAction().doDynamic(req, rsp);
		}

		verify(rsp).setStatus(SC_BAD_REQUEST);
		String body = sw.toString();
		assertTrue(body.contains("already exists"));
		assertTrue(body.contains("ALREADY_EXISTS"));
	}
}
