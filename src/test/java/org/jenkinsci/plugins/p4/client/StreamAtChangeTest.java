package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.IDepot;
import com.perforce.p4java.core.IStream;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.core.IStreamViewMapping;
import com.perforce.p4java.core.IStreamlog;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.core.Depot;
import com.perforce.p4java.impl.generic.core.Stream;
import com.perforce.p4java.option.server.StreamOptions;
import com.perforce.p4java.option.server.StreamlogOptions;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.SpecWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StreamAtChangeTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-WorkspaceSpecTest-p4root";
	private P4PasswordImpl credentials;
	private ConnectionHelper p4;
	private IOptionsServer server;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R19_1);

	@Before
	public void buildCredentials() throws Exception {
		credentials = createCredentials("jenkins", "Password", p4d.getRshPort(), CREDENTIAL);
		p4 = new ConnectionHelper(credentials);
		server = p4.getConnection();
	}

	@After
	public void tearDown() {
		if (p4 != null) {
			p4.disconnect();
		}
	}

	@Test
	public void testStreamAtChange() throws Exception {
		submitFile(jenkins, "//depot/streamAtChange/depotFile", "This is first file.");
		//Create two version of a stream
		String streamPath = createStream();

		//Get change number for the stream updates
		List<String> path = new ArrayList<>();
		path.add(streamPath);
		Map<String, List<IStreamlog>> streamLog = server.getStreamlog(path, new StreamlogOptions());
		List<Integer> collect = streamLog.values().stream()
				.flatMap(Collection::stream)
				.map(IStreamlog::getChange)
				.sorted().toList();
		int firstVersion = collect.get(0);
		int secondVersion = collect.get(1);

		// Build with second stream revision. It should contain imported file "depotFile"
		FreeStyleBuild build = getFreeStyleBuild(streamPath, secondVersion, "ClientWithSecondStreamVersion");
		assertEquals(Result.SUCCESS, build.getResult());
		jenkins.assertLogContains("//depot/streamAtChange/depotFile", build);

		// Build with first stream revision. It should not contain file "depotFile". As this file is not included in view.
		build = getFreeStyleBuild(streamPath, firstVersion, "ClientWithFirstStreamVersion");
		jenkins.assertLogNotContains("//depot/streamAtChange/depotFile", build);

	}

	@Test
	public void testFreeStyleProject_SpecWs() throws Exception {
		submitFile(jenkins, "//depot/streamAtChange/depotFile", "This is first file.");
		//Create two version of a stream
		String streamPath = createStream();

		//Get change number for the stream updates
		List<String> path = new ArrayList<>();
		path.add(streamPath);
		Map<String, List<IStreamlog>> streamLog = server.getStreamlog(path, new StreamlogOptions());
		List<Integer> collect = streamLog.values().stream()
				.flatMap(Collection::stream)
				.map(IStreamlog::getChange)
				.sorted().toList();
		int secondVersion = collect.get(1);

		String stream = "//stream/atChange";

		String client = "jenkins-${JOB_NAME}";
		String specPath = "//depot/spec/test1";

		String specFile = ""
				+ "Client: jenkins-${JOB_NAME}\n"
				+ "Owner: pallen\n"
				+ "Root: /tmp\n"
				+ "Options:	noallwrite noclobber nocompress unlocked nomodtime rmdir\n"
				+ "SubmitOptions: submitunchanged\n"
				+ "LineEnd:	local\n"
				+ "Stream: "+stream+"\n"
				+ "StreamAtChange: " + secondVersion + "\n";

		submitFile(jenkins, specPath, specFile);

		FreeStyleProject project = jenkins.createFreeStyleProject("Spec-Head");

		// Create client from the spec file
		SpecWorkspaceImpl workspace = new SpecWorkspaceImpl("none", false, client, specPath);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		FreeStyleBuild build;
		Cause.UserIdCause cause = new Cause.UserIdCause();
		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		WorkspaceDescriptor desc = workspace.getDescriptor();
		assertNotNull(desc);
		assertEquals("Spec File (load workspace spec from file in Perforce)", desc.getDisplayName());

		jenkins.assertLogContains("//depot/streamAtChange/depotFile", build);
	}

	private String createStream() throws P4JavaException {
		//1 . Create stream depot
		Depot depot = new Depot("stream", server.getUserName(), null, "Stream Depot", IDepot.DepotType.STREAM, null, null, "//stream/...");
		server.createDepot(depot);

		//2. Create stream
		String streamPath = "//stream/atChange";
		IStream stream = new Stream();
		stream.setOwnerName(server.getUserName());
		stream.setStream(streamPath);
		stream.setName("atChange");
		stream.setType(IStreamSummary.Type.MAINLINE);

		ViewMap<IStreamViewMapping> streamView = new ViewMap<>();
		streamView.addEntry(new Stream.StreamViewMapping(0, IStreamViewMapping.PathType.SHARE, "...", null));
		stream.setStreamView(streamView);
		server.createStream(stream);

		//3. Update stream view to include file "//depot/streamAtChange/depotFile".
		IStream stream1 = server.getStream(streamPath);
		ViewMap<IStreamViewMapping> streamViewNew = new ViewMap<>();
		streamViewNew.addEntry(new Stream.StreamViewMapping(0, IStreamViewMapping.PathType.SHARE, "...", null));
		streamViewNew.addEntry(new Stream.StreamViewMapping(0, IStreamViewMapping.PathType.IMPORT, "streamAtChange/...", "//depot/streamAtChange/..."));
		stream1.setStreamView(streamViewNew);
		server.updateStream(stream1, new StreamOptions());
		return streamPath;
	}

	private static FreeStyleBuild getFreeStyleBuild(String streamPath, int secondVersion, String clientName) throws IOException, InterruptedException, ExecutionException {
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}.ws";
		WorkspaceSpec spec = new WorkspaceSpec(false, true, false, false, false, false, streamPath, "LOCAL", "", null, null, null, false);
		spec.setStreamAtChange(String.valueOf(secondVersion));
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, format, spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		FreeStyleProject project = jenkins.createFreeStyleProject(clientName);
		project.setScm(scm);
		project.save();

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		return build;
	}
}
