package org.jenkinsci.plugins.p4.unit.review;

import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.review.ReviewAction;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewActionTest {

	@Test
	void testSimpleAccessors() {
		FreeStyleProject project = mock(FreeStyleProject.class);
		ReviewAction<FreeStyleProject> action = new ReviewAction<>(project);

		assertEquals(project, action.getProject());
		assertEquals("symbol-swarm-review plugin-p4", action.getIconFileName());
		assertEquals("Build Review", action.getDisplayName());
		assertEquals("review", action.getUrlName());
	}

	@Test
	void testGetAvailableParameters() {
		FreeStyleProject project = mock(FreeStyleProject.class);
		ReviewAction<FreeStyleProject> action = new ReviewAction<>(project);

		List<StringParameterValue> params = action.getAvailableParameters();

		assertEquals(7, params.size());
		assertTrue(params.stream().anyMatch(p -> p.getName().equals("review")));
		assertTrue(params.stream().anyMatch(p -> p.getName().equals("p4.label")));
	}

	@Test
	void testDoBuildSchedulesQueueRemovesInternalParamsAndRedirects() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		when(project.getQuietPeriod()).thenReturn(5000);

		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getParameterNames()).thenReturn(Collections.enumeration(List.of("review", "myCustomParam")));
		when(req.getParameterValues("review")).thenReturn(new String[]{"19"});
		when(req.getParameterValues("myCustomParam")).thenReturn(new String[]{"hello"});

		Jenkins jenkins = mock(Jenkins.class);
		Queue queue = mock(Queue.class);
		when(jenkins.getQueue()).thenReturn(queue);

		ReviewAction<FreeStyleProject> action = new ReviewAction<>(project);

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);
			action.doBuild(req, rsp);
		}

		verify(project).checkPermission(Item.BUILD);
		verify(queue).schedule(eq(project), eq(5), any(Action.class), any(Action.class));
		verify(rsp).sendRedirect("../");
	}

	@Test
	void testDoBuildSubmitSkipsBuildWhenFormEmpty() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		when(req.getSubmittedForm()).thenReturn(new JSONObject());

		ReviewAction<FreeStyleProject> action = new ReviewAction<>(project);
		action.doBuildSubmit(req, rsp);

		verify(project).checkPermission(Item.BUILD);
		verify(rsp, never()).sendRedirect(any());
	}

	@Test
	void testDoBuildSubmitBuildsWhenFormNotEmpty() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		when(project.getQuietPeriod()).thenReturn(0);

		StaplerRequest2 req = mock(StaplerRequest2.class);
		StaplerResponse2 rsp = mock(StaplerResponse2.class);
		JSONObject formData = new JSONObject();
		formData.element("foo", "bar");
		when(req.getSubmittedForm()).thenReturn(formData);
		when(req.getParameterNames()).thenReturn(Collections.enumeration(List.of()));

		Jenkins jenkins = mock(Jenkins.class);
		Queue queue = mock(Queue.class);
		when(jenkins.getQueue()).thenReturn(queue);

		ReviewAction<FreeStyleProject> action = new ReviewAction<>(project);

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);
			action.doBuildSubmit(req, rsp);
		}

		verify(rsp).sendRedirect("../");
	}
}
