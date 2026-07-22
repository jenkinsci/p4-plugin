package org.jenkinsci.plugins.p4.unit.changes;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.graph.ICommit;
import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeParser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeLogHandlerTest {

	private final Run<?, ?> run = mock(Run.class);

	@Test
	void testConstructorSkipsSwarmLookupWhenBrowserProvided() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		RepositoryBrowser<?> browser = mock(RepositoryBrowser.class);

		new P4ChangeParser.ChangeLogHandler(run, browser, p4);

		verify(p4, never()).getSwarm();
	}

	@Test
	void testConstructorUsesSwarmUrlWhenBrowserMissing() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		when(p4.getSwarm()).thenReturn("http://swarm.example.com/");

		P4ChangeParser.ChangeLogHandler handler = new P4ChangeParser.ChangeLogHandler(run, null, p4);
		handler.startDocument();

		assertInstanceOf(SwarmBrowser.class, handler.getChangeLogSet().getBrowser());
	}

	@Test
	void testConstructorLeavesBrowserNullWhenNoSwarmUrl() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		when(p4.getSwarm()).thenReturn(null);

		P4ChangeParser.ChangeLogHandler handler = new P4ChangeParser.ChangeLogHandler(run, null, p4);
		handler.startDocument();

		assertNull(handler.getChangeLogSet().getBrowser());
	}

	@Test
	void testConstructorIgnoresUnknownCommandError() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		when(p4.getSwarm()).thenThrow(new RequestException("Unknown command swarm"));

		P4ChangeParser.ChangeLogHandler handler = new P4ChangeParser.ChangeLogHandler(run, null, p4);
		handler.startDocument();

		assertNull(handler.getChangeLogSet().getBrowser());
	}

	@Test
	void testConstructorPropagatesOtherRequestExceptions() {
		ConnectionHelper p4 = mock(ConnectionHelper.class);

		assertThrows(RequestException.class, () -> {
			when(p4.getSwarm()).thenThrow(new RequestException("connection refused"));
			new P4ChangeParser.ChangeLogHandler(run, null, p4);
		});
	}

	@Test
	void testParsingPopulatesEntryFields() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		RepositoryBrowser<?> browser = mock(RepositoryBrowser.class);

		ICommit commit = mock(ICommit.class);
		when(commit.getCommitterDate()).thenReturn(new Date());
		when(p4.getGraphCommit("sha1", "repo")).thenReturn(commit);

		String xml = "<changelog>"
				+ "<entry>"
				+ "<changeInfo>1234</changeInfo>"
				+ "<shelved>true</shelved>"
				+ "<fileLimit>false</fileLimit>"
				+ "<msg>hello world</msg>"
				+ "<clientId>my-client</clientId>"
				+ "<changeTime>2024-01-02 03:04:05</changeTime>"
				+ "<file depot=\"//depot/foo.txt\" action=\"EDIT\" endRevision=\"3\"/>"
				+ "<job id=\"JOB-1\" status=\"closed\"/>"
				+ "</entry>"
				+ "<entry>"
				+ "<changeInfo>repo@sha1</changeInfo>"
				+ "</entry>"
				+ "</changelog>";

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(null);

			P4ChangeParser.ChangeLogHandler handler = new P4ChangeParser.ChangeLogHandler(run, browser, p4);
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.newSAXParser().parse(new InputSource(new StringReader(xml)), handler);

			List<P4ChangeEntry> history = handler.getChangeLogSet().getHistory();
			assertEquals(2, history.size());

			P4ChangeEntry first = history.get(0);
			assertEquals("1234", first.getChangeNumber());
			assertTrue(first.isShelved());
			assertFalse(first.isFileLimit());
			assertEquals("hello world", first.getMsg());
			assertEquals("my-client", first.getClientId());
			assertEquals("2024-01-02 03:04:05", first.getChangeTime());
			assertEquals(1, first.getAffectedFiles().size());
			assertEquals(1, first.getJobs().size());
			assertEquals("closed", first.getJobStatus(first.getJobs().get(0)));

			P4ChangeEntry second = history.get(1);
			assertEquals("repo@sha1", second.getChangeNumber());
		}
	}

	@Test
	void testUserDependentBranchesAreCaughtAndSwallowed() throws Exception {
		ConnectionHelper p4 = mock(ConnectionHelper.class);
		RepositoryBrowser<?> browser = mock(RepositoryBrowser.class);

		IChangelistSummary summary = mock(IChangelistSummary.class);
		when(summary.getId()).thenReturn(99);
		when(summary.getUsername()).thenReturn("alice");
		when(p4.getChangeSummary(99)).thenReturn(summary);

		String xml = "<changelog>"
				+ "<entry>"
				+ "<changenumber>99</changenumber>"
				+ "<changeUser>alice</changeUser>"
				+ "<msg>still set</msg>"
				+ "</entry>"
				+ "</changelog>";

		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(null);

			P4ChangeParser.ChangeLogHandler handler = new P4ChangeParser.ChangeLogHandler(run, browser, p4);
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.newSAXParser().parse(new InputSource(new StringReader(xml)), handler);

			List<P4ChangeEntry> history = handler.getChangeLogSet().getHistory();
			assertEquals(1, history.size());
			// changenumber/changeUser throw internally (no real Jenkins User db) and are silently
			// swallowed by the handler's own try/catch; msg is handled in a later, independent
			// endElement call so it still applies.
			assertEquals("still set", history.get(0).getMsg());
		}
	}
}
