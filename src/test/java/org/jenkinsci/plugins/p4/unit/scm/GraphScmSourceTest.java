package org.jenkinsci.plugins.p4.unit.scm;

import com.perforce.p4java.core.IRepo;
import com.perforce.p4java.graph.IGraphRef;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.changes.P4PollRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.scm.GraphScmSource;
import org.jenkinsci.plugins.p4.scm.P4Path;
import org.jenkinsci.plugins.p4.scm.P4SCMHead;
import org.jenkinsci.plugins.p4.scm.P4SCMRevision;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class GraphScmSourceTest {

	private static final String CREDENTIAL = "credential-id";

	private GraphScmSource newSource() {
		return new GraphScmSource(CREDENTIAL, "//depot/repos/...", "none", "jenkins-${NODE_NAME}-${JOB_NAME}");
	}

	@Test
	void testBrowserGetterAndSetter() {
		GraphScmSource source = newSource();
		assertNull(source.getBrowser());

		P4Browser browser = mock(P4Browser.class);
		source.setBrowser(browser);

		assertSame(browser, source.getBrowser());
	}

	@Test
	void testGetHeadsReturnsBranchHeadsFromRepos() throws Exception {
		GraphScmSource source = newSource();
		TaskListener listener = mock(TaskListener.class);

		IRepo repo = mock(IRepo.class);
		when(repo.getName()).thenReturn("myrepo.git");

		IGraphRef ref = mock(IGraphRef.class);
		when(ref.getName()).thenReturn("main");

		IOptionsServer server = mock(IOptionsServer.class);
		when(server.getGraphShowRefs(any())).thenReturn(List.of(ref));

		try (MockedConstruction<ConnectionHelper> mocked = mockConstruction(ConnectionHelper.class, (mock, context) -> {
			when(mock.listRepos("//depot/repos/...")).thenReturn(List.of(repo));
			when(mock.getConnection()).thenReturn(server);
		})) {
			List<P4SCMHead> heads = source.getHeads(listener);

			assertEquals(1, heads.size());
			assertEquals("myrepo.main", heads.get(0).getName());
		}
	}

	@Test
	void testGetTagsReturnsOnlyMergeRefs() throws Exception {
		GraphScmSource source = newSource();
		TaskListener listener = mock(TaskListener.class);

		IRepo repo = mock(IRepo.class);
		when(repo.getName()).thenReturn("myrepo");

		IGraphRef mergeRef = mock(IGraphRef.class);
		when(mergeRef.getName()).thenReturn("refs/pull/1/merge");
		IGraphRef headRef = mock(IGraphRef.class);
		when(headRef.getName()).thenReturn("refs/pull/1/head");

		IOptionsServer server = mock(IOptionsServer.class);
		when(server.getGraphShowRefs(any())).thenReturn(List.of(mergeRef, headRef));

		try (MockedConstruction<ConnectionHelper> mocked = mockConstruction(ConnectionHelper.class, (mock, context) -> {
			when(mock.listRepos("//depot/repos/...")).thenReturn(List.of(repo));
			when(mock.getConnection()).thenReturn(server);
		})) {
			List<P4SCMHead> tags = source.getTags(listener);

			assertEquals(1, tags.size());
		}
	}

	@Test
	void testGetRevisionDelegatesToGraphHead() {
		GraphScmSource source = newSource();
		TempClientHelper p4 = mock(TempClientHelper.class);
		P4Path path = new P4Path("myrepo");
		P4SCMHead head = new P4SCMHead("myrepo.main", path);
		P4Ref ref = new P4PollRef(10, "myrepo");
		when(p4.getGraphHead("myrepo")).thenReturn(ref);

		P4SCMRevision revision = source.getRevision(p4, head);

		assertSame(ref, revision.getRef());
		assertSame(head, revision.getHead());
	}

	@Test
	void testGetWorkspaceBuildsManualWorkspace() {
		GraphScmSource source = newSource();
		P4Path path = new P4Path("myrepo");

		Workspace workspace = source.getWorkspace(path);

		assertInstanceOf(ManualWorkspaceImpl.class, workspace);
	}

	@Test
	void testGetWorkspaceThrowsForNullPath() {
		GraphScmSource source = newSource();
		assertThrows(IllegalArgumentException.class, () -> source.getWorkspace(null));
	}
}
