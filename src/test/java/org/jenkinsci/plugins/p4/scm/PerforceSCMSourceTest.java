package org.jenkinsci.plugins.p4.scm;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.perforce.p4java.core.IStream;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.core.IStreamViewMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.core.Stream;
import com.perforce.p4java.impl.generic.core.StreamSummary;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.ExtendedJenkinsRule;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.events.P4BranchSCMHeadEvent;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmProjectAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewsAPI;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PerforceSCMSourceTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(PerforceSCMSourceTest.class.getName());

	private static final String P4ROOT = "tmp-ScmSourceTest-p4root";

	@ClassRule
	public static ExtendedJenkinsRule jenkins = new ExtendedJenkinsRule(30 * 60);

	@ClassRule
	public static SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void testMultiBranchWithStreams() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		StreamsScmSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-streams");
		multi.getSourcesList().add(new BranchSource(source));

		// Get a connection and create the virtual stream.
		ConnectionHelper p4 = new ConnectionHelper(source.getOwner(), CREDENTIAL, null);
		createVirtualStream(p4.getConnection());

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		WorkflowJob job = multi.getItem("Ace-main");

		assertThat("We now have a branch", job, notNullValue());

		WorkflowRun build = job.getLastBuild();

		assertThat("The branch was built", build, notNullValue());
		assertThat("The branch was built", build.getNumber(), is(1));

		// Check for Virtual Stream
		job = multi.getItem("Ace-virtual");

		assertThat("We now have a branch", job, notNullValue());

		build = job.getLastBuild();

		assertThat("The branch was built", build, notNullValue());
		assertThat("The branch was built", build.getNumber(), is(1));

	}

	@Test
	public void testMultiBranchWithClassic() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		WorkflowJob job = multi.getItem("Ace-main");

		assertThat("We now have a branch", job, notNullValue());

		WorkflowRun build = job.getLastBuild();

		assertThat("The branch was built", build, notNullValue());
		assertThat("The branch was built", build.getNumber(), is(1));
	}

	@Test
	public void testExcludesWithClassic() throws Exception {

		String project = "excludeClassic";
		String base = "//depot/" + project;
		String[] branches = new String[]{"br1", "br2"};

		sampleProject(base, branches, "Jenkinsfile");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setExcludes(".*2");

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
		assertNotNull(multi.getItem("br1"));
		assertEquals(1, multi.getItems().size());
	}

	@Test
	public void testAJobShouldNotCreateClientForOtherJobsInAMultibranchProject() throws Exception {

		String project = "multi";
		String base = "//depot/" + project;
		String[] branches = new String[]{"br1", "br2"};

		sampleProject(base, branches, "Jenkinsfile");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		WorkflowJob job = multi.getItem("br1");
		File logFile = Objects.requireNonNull(job).getLastBuild().getLogFile();
		BufferedReader br = new BufferedReader(new FileReader(logFile));
		StringBuilder builder = new StringBuilder();
		String line;
		while((line = br.readLine()) !=null){
			builder.append(line);
		}
		assertTrue("A Job should include its branch in console logs",builder.toString().contains("//depot/multi/br1"));
		assertFalse("A job should not include other job branch in console logs",builder.toString().contains("//depot/multi/br2"));
	}

	@Test
	public void testExcludesWithSwarm() throws Exception {

		String project = "excludeSwarm";
		String base = "//depot/" + project;
		String[] branches = new String[]{"br1", "br2"};

		sampleProject(base, branches, "Jenkinsfile");

		SwarmHelper mockSwarm = sampleSwarmProject(project, base, branches);
		assertNotNull(mockSwarm);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		SwarmScmSource source = new SwarmScmSource(CREDENTIAL, null, format);
		source.setProject(project);
		source.setExcludes(".*2");
		source.setSwarm(mockSwarm);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
		assertNotNull(multi.getItem("br1"));
		assertEquals(1, multi.getItems().size());
	}

	@Test
	public void testNoMultiStreams() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//depot/...";
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "no-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We have no branches", multi.getItems(), containsInAnyOrder());
	}

	@Test
	public void testSingleStream() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/Ace-main";
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "single-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testSimplePathStreams() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "path-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testWildPathStreams() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/Ace-*";
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "wild-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testExcludesStreams() throws Exception {

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "excludes-streams");

		CredentialsStore folderStore = getFolderStore(multi);
		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInFolder", "desc:passwd", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);

		// Get a connection
		ConnectionHelper p4 = new ConnectionHelper(inFolderCredentials);
		IOptionsServer server = p4.getConnection();

		// create a Mainline stream
		IStream stream = new Stream();
		stream.setOwnerName(server.getUserName());
		stream.setStream("//stream/Acme-main");
		stream.setName("Acme-main");
		stream.setType(IStreamSummary.Type.MAINLINE);

		// add a view mapping
		ViewMap<IStreamViewMapping> streamView = new ViewMap<>();
		streamView.addEntry(new Stream.StreamViewMapping(0, IStreamViewMapping.PathType.SHARE, "...", null));
		stream.setStreamView(streamView);
		server.createStream(stream);

		// Create a Jenkinsfile
		String pipeline = """
				\
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        echo "Hello"
				      }
				    }
				  }
				}""";
		submitStreamFile(jenkins, "//stream/Acme-main/Jenkinsfile", pipeline, "description");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		StreamsScmSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);
		source.setExcludes("Ace.*");

		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
		assertNotNull(multi.getItem("Acme-main"));
		assertNull(multi.getItem("Ace-main"));
	}

	@Test
	public void testSimplePathClassic() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "path-classic");
		multi.getLastBuiltOn().setLabelString("master");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testStarPathClassic() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/*";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "star-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.getLastBuiltOn().setLabelString("master");
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testRootPathClassic() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//...";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "root-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testSpacePathClassic() throws Exception {

		submitFile(jenkins, "//depot/space path/A/Jenkinsfile", "node() {}");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//depot/space path/...";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "space-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testMappingPathClassic() throws Exception {

		submitFile(jenkins, "//depot/classic/A/src/fileA", "content");
		submitFile(jenkins, "//depot/classic/A/tests/fileB", "content");
		submitFile(jenkins, "//depot/classic/A/Jenkinsfile", """
				\
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        script {
				          if(!fileExists('Jenkinsfile')) error 'missing Jenkinsfile'
				          if(!fileExists('fileA'))       error 'missing fileA'
				          if(!fileExists('fileB'))       error 'missing fileB'
				        }
				      }
				    }
				  }
				}""");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//depot/classic/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());
		String mappings = "src/...\ntests/...";
		source.setMappings(mappings);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "mapping-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		WorkflowJob job = multi.getItem("A");
		assertThat("We now have a branch", job, notNullValue());

		WorkflowRun build = job.getLastBuild();
		assertThat("The branch was built", build, notNullValue());
		assertEquals(Result.SUCCESS, build.getResult());
	}

	@Test
	public void testMappingDefaultsClassic() throws Exception {

		String base = "//depot/default";
		sampleProject(base, new String[]{"Main"}, "Jenkinsfile");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "mapping-default-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		WorkflowJob job = multi.getItem("Main");
		assertThat("We now have a branch", job, notNullValue());

		WorkflowRun build = job.getLastBuild();
		assertThat("The branch was built", build, notNullValue());
		assertEquals(Result.SUCCESS, build.getResult());
	}

	@Test
	public void testRootPathStreams() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//...";
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "root-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testMultiBranchClassicWithCredentialsInFolder() throws Exception {

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-classic-creds-in-folder");

		CredentialsStore folderStore = getFolderStore(multi);
		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInFolder", "desc:passwd", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new BranchesScmSource(inFolderCredentials.getId(), includes, null, format);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals("Branch Indexing succeeded", Result.SUCCESS, multi.getComputation().getResult());
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testMultiBranchStreamWithCredentialsInFolder() throws Exception {

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-streams-creds-in-folder");

		CredentialsStore folderStore = getFolderStore(multi);
		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInFolder", "desc:passwd", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new StreamsScmSource(inFolderCredentials.getId(), includes, null, format);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals("Branch Indexing succeeded", Result.SUCCESS, multi.getComputation().getResult());
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testMultiBranchStreamWithImports() throws Exception {

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-streams-imports");

		CredentialsStore folderStore = getFolderStore(multi);
		P4BaseCredentials inFolderCredentials = new P4PasswordImpl(
				CredentialsScope.GLOBAL, "idInFolder", "desc:passwd", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);

		// Get a connection
		ConnectionHelper p4 = new ConnectionHelper(inFolderCredentials);
		IOptionsServer server = p4.getConnection();

		// create a Mainline stream
		IStream stream = new Stream();
		stream.setOwnerName(server.getUserName());
		stream.setStream("//stream/import");
		stream.setName("import");
		stream.setType(IStreamSummary.Type.MAINLINE);

		// add a view with import+ mapping
		ViewMap<IStreamViewMapping> streamView = new ViewMap<>();
		streamView.addEntry(new Stream.StreamViewMapping(0, IStreamViewMapping.PathType.SHARE, "...", null));
		streamView.addEntry(new Stream.StreamViewMapping(1, IStreamViewMapping.PathType.IMPORTPLUS, "imports/...", "//depot/import_test/..."));
		stream.setStreamView(streamView);
		server.createStream(stream);

		// Create a Jenkinsfile
		String pipeline = """
				\
				pipeline {
				  agent any
				  stages {
				    stage('Test') {
				      steps {
				        script {
				          if(!fileExists('imports/file1.txt'))   error 'missing import'
				        }
				      }
				    }
				  }
				}""";
		submitStreamFile(jenkins, "//stream/import/Jenkinsfile", pipeline, "description");

		// create a file to import
		submitFile(jenkins, "//depot/import_test/file1.txt", "content");

		// create multi-branch project with one branch
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/imp...";
		StreamsScmSource source = new StreamsScmSource(inFolderCredentials.getId(), includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals("Branch Indexing succeeded", Result.SUCCESS, multi.getComputation().getResult());
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// create a new change on the imported path
		String change = submitFile(jenkins, "//depot/import_test/file2.txt", "content");
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		WorkflowJob job = multi.getItem("import");
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		assertEquals(1, job.getLastBuild().getChangeSets().size());
		P4ChangeSet changeSet1 = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(1, changeSet1.getHistory().size());
		assertEquals(change, changeSet1.getHistory().get(0).getId().toString());
	}

	@Test
	public void testMultiBranchClassicUpdateEvent() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/update";
		String baseChange = sampleProject(base, new String[]{"Main", "Dev"}, "Jenkinsfile");
		assertNotNull(baseChange);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "classic-update-event");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Make a change
		String change = submitFile(jenkins, base + "/" + branch + "/src/fileA", "edit");

		HashMap<String, String> map = new HashMap<>();
		map.put(ReviewProp.P4_PORT.getProp(), p4d.getRshPort());
		map.put(ReviewProp.P4_CHANGE.getProp(), change);
		JSONObject payload = JSONObject.fromObject(map);

		String origin = "testMultiBranchClassicUpdateEvent";
		P4BranchSCMHeadEvent event = new P4BranchSCMHeadEvent(SCMEvent.Type.UPDATED, payload, origin);
		SCMHeadEvent.fireNow(event);

		waitForBuild(job, 2);
		jenkins.waitUntilNoActivity();

		WorkflowRun build = multi.getItem("Main").getLastBuild();
		assertEquals("Main has built", 2, build.number);
		assertEquals("Dev has not built", 1, multi.getItem("Dev").getLastBuild().number);
	}

	@Test
	public void testMultiBranchClassicMultiUpdateEvents() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/multi";
		String baseChange = sampleProject(base, new String[]{"Main", "Dev"}, "Jenkinsfile");
		assertNotNull(baseChange);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-update-events");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Make a change
		String change = submitFile(jenkins, base + "/" + branch + "/src/fileA", "edit1");

		HashMap<String, String> map = new HashMap<>();
		map.put(ReviewProp.P4_PORT.getProp(), p4d.getRshPort());
		map.put(ReviewProp.P4_CHANGE.getProp(), change);
		JSONObject payload = JSONObject.fromObject(map);

		// Another change that should not get sync'ed
		submitFile(jenkins, base + "/" + branch + "/src/fileB", "edit2");

		String origin = "testMultiBranchClassicMultiUpdateEvents";
		P4BranchSCMHeadEvent event = new P4BranchSCMHeadEvent(SCMEvent.Type.UPDATED, payload, origin);
		SCMHeadEvent.fireNow(event);

		waitForBuild(job, 2);
		jenkins.waitUntilNoActivity();

		WorkflowRun runMain = multi.getItem("Main").getLastBuild();
		assertEquals("Main has built", 2, runMain.number);
		assertEquals(Result.SUCCESS, runMain.getResult());

		assertEquals("Dev has not built", 1, multi.getItem("Dev").getLastBuild().number);
		jenkins.assertLogContains("P4 Task: syncing files at change: " + change, runMain);
	}

	@Test
	public void testMultiBranchSwarmCommittedTriggerEvent() throws Exception {

		// Setup sample Multi Branch Project
		String project = "SwarmTriggerCommit";
		String base = "//depot/SwarmTriggerCommit";
		String[] branches = new String[]{"Main", "Dev"};

		String baseChange = sampleProject(base, branches, "Jenkinsfile");
		assertNotNull(baseChange);

		SwarmHelper mockSwarm = sampleSwarmProject(project, base, branches);
		assertNotNull(mockSwarm);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		SwarmScmSource source = new SwarmScmSource(CREDENTIAL, null, format);
		source.setProject(project);
		source.setSwarm(mockSwarm);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Make a shelve / fake review
		String commit = submitFile(jenkins, base + "/" + branch + "/src/fileA", "edit1");
		assertNotNull(commit);
		assertTrue("Not a number", commit.chars().allMatch(Character::isDigit));

		// Mock Changes/Reviews
		List<Long> changes = new ArrayList<>();
		changes.add(Long.parseLong(commit));
		HashMap<String, List<String>> projects = new HashMap<>();
		projects.put(project, List.of("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects, "author");
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(List.of(mockReview)));

		// Build JSON Payload
		HashMap<String, String> map = new HashMap<>();
		map.put(ReviewProp.P4_PORT.getProp(), p4d.getRshPort());
		map.put(ReviewProp.P4_CHANGE.getProp(), commit);

		JSONObject payload = JSONObject.fromObject(map);

		// Another change that should not get sync'ed
		submitFile(jenkins, base + "/" + branch + "/src/fileB", "edit2");

		String origin = "testMultiBranchSwarmCommittedTriggerEvent";
		P4BranchSCMHeadEvent event = new P4BranchSCMHeadEvent(SCMEvent.Type.UPDATED, payload, origin);
		SCMHeadEvent.fireNow(event);

		waitForBuild(multi.getItem("Main"), 2);
		jenkins.waitUntilNoActivity();

		assertEquals("Dev should not build", 1, multi.getItem("Dev").getLastBuild().number);

		WorkflowRun runMain = multi.getItem("Main").getLastBuild();
		assertEquals("Main should have built", 2, runMain.number);
		assertEquals(Result.SUCCESS, runMain.getResult());

		jenkins.assertLogContains("P4 Task: syncing files at change: " + commit, runMain);
	}

	@Test
	public void testMultiBranchSwarmCommittedAPIEvent() throws Exception {

		// Setup sample Multi Branch Project
		String project = "SwarmCommit";
		String base = "//depot/SwarmCommit";
		String[] branches = new String[]{"Main", "Dev"};

		String baseChange = sampleProject(base, branches, "Jenkinsfile");
		assertNotNull(baseChange);

		SwarmHelper mockSwarm = sampleSwarmProject(project, base, branches);
		assertNotNull(mockSwarm);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		SwarmScmSource source = new SwarmScmSource(CREDENTIAL, null, format);
		source.setProject(project);
		source.setSwarm(mockSwarm);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Make a shelve / fake review
		String commit = submitFile(jenkins, base + "/" + branch + "/src/fileA", "edit1");
		assertNotNull(commit);
		assertTrue("Not a number", commit.chars().allMatch(Character::isDigit));

		// Mock Changes/Reviews
		List<Long> changes = new ArrayList<>();
		changes.add(Long.parseLong(commit));
		HashMap<String, List<String>> projects = new HashMap<>();
		projects.put(project, List.of("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects, "author");
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(List.of(mockReview)));

		// Build JSON Payload
		HashMap<String, String> map = new HashMap<>();
		map.put(ReviewProp.P4_PORT.getProp(), p4d.getRshPort());
		map.put(ReviewProp.SWARM_PROJECT.getProp(), project);
		map.put(ReviewProp.SWARM_BRANCH.getProp(), branch);
		map.put(ReviewProp.SWARM_PATH.getProp(), base + "/" + branch);
		map.put(ReviewProp.P4_CHANGE.getProp(), commit);
		map.put(ReviewProp.SWARM_STATUS.getProp(), CheckoutStatus.COMMITTED.name());

		JSONObject payload = JSONObject.fromObject(map);

		// Another change that should not get sync'ed
		submitFile(jenkins, base + "/" + branch + "/src/fileB", "edit2");

		String origin = "testMultiBranchClassicUpdateEvent";
		P4BranchSCMHeadEvent event = new P4BranchSCMHeadEvent(SCMEvent.Type.UPDATED, payload, origin);
		SCMHeadEvent.fireNow(event);

		waitForBuild(multi.getItem("Main"), 2);
		jenkins.waitUntilNoActivity();

		assertEquals("Dev should not build", 1, multi.getItem("Dev").getLastBuild().number);

		WorkflowRun runMain = multi.getItem("Main").getLastBuild();
		assertEquals("Main should have built", 2, runMain.number);

		jenkins.assertLogContains("P4 Task: syncing files at change: " + commit, runMain);
		assertEquals(Result.SUCCESS, runMain.getResult());
	}

	@Test
	public void testMultiBranchSwarmMultiUpdateEvents() throws Exception {

		// Setup sample Multi Branch Project
		String project = "SwarmReview";
		String base = "//depot/SwarmReview";
		String[] branches = new String[]{"Main", "Dev"};

		String baseChange = sampleProject(base, branches, "Jenkinsfile");
		assertNotNull(baseChange);

		SwarmHelper mockSwarm = sampleSwarmProject(project, base, branches);
		assertNotNull(mockSwarm);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		SwarmScmSource source = new SwarmScmSource(CREDENTIAL, null, format);
		source.setProject(project);
		source.setSwarm(mockSwarm);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, project);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());

		// Make a shelve / fake review
		String review = shelveFile(jenkins, base + "/" + branch + "/src/fileA", "edit1");
		logger.info("Test: shelving " + review);
		assertNotNull(review);
		assertTrue("Not a number", review.chars().allMatch(Character::isDigit));

		// Mock Changes/Reviews
		List<Long> changes = new ArrayList<>();
		changes.add(Long.parseLong(review));
		HashMap<String, List<String>> projects = new HashMap<>();
		projects.put(project, List.of("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects, "author");
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(List.of(mockReview)));

		List<SwarmReviewsAPI.Reviews> mockReviewsList = new ArrayList<>();
		SwarmReviewsAPI.Reviews mockReviews = new SwarmReviewsAPI.Reviews(Long.parseLong(review), changes, "author");
		mockReviewsList.add(mockReviews);
		when(mockSwarm.getActiveReviews(project)).thenReturn(mockReviewsList);

		// Build JSON Payload
		HashMap<String, String> map = new HashMap<>();
		map.put(ReviewProp.P4_PORT.getProp(), p4d.getRshPort());
		map.put(ReviewProp.SWARM_PROJECT.getProp(), project);
		map.put(ReviewProp.SWARM_BRANCH.getProp(), branch);
		map.put(ReviewProp.SWARM_PATH.getProp(), base + "/" + branch);
		map.put(ReviewProp.P4_CHANGE.getProp(), review);
		map.put(ReviewProp.SWARM_REVIEW.getProp(), review);
		map.put(ReviewProp.SWARM_STATUS.getProp(), CheckoutStatus.SHELVED.name());

		JSONObject payload = JSONObject.fromObject(map);

		// Another change that should not get sync'ed
		String change = submitFile(jenkins, base + "/" + branch + "/src/fileB", "edit2");
		logger.info("Test: submitting change " + change);

		String origin = "testMultiBranchSwarmMultiUpdateEvents";
		P4BranchSCMHeadEvent event = new P4BranchSCMHeadEvent(SCMEvent.Type.CREATED, payload, origin);
		logger.fine("\n\nTest: Firing Event!");
		SCMHeadEvent.fireNow(event);

		WorkflowJob revJob = multi.getItem(review);

		// wait for review build to start...
		int tries = 100;
		while (revJob == null && tries > 0) {
			tries--;
			revJob = multi.getItem(review);
			Thread.sleep(100);
		}
		assertNotNull(revJob);

		jenkins.waitUntilNoActivity();

		assertEquals("Dev should not built", 1, multi.getItem("Dev").getLastBuild().number);
		assertEquals("Main should not built", 1, multi.getItem("Main").getLastBuild().number);

		WorkflowRun revRun = revJob.getLastBuild();
		assertNotNull(revJob);

		jenkins.assertLogContains("P4 Task: syncing files at change: " + review, revRun);
		jenkins.assertLogContains("P4 Task: unshelve review: " + review, revRun);
		assertEquals(Result.SUCCESS, revRun.getResult());
	}

	@Test
	@Issue("JENKINS-38781")
	public void testUnshelvedChangesShouldGetDisplayedInJenkinsBuildChange() throws Exception {
		String base = "//depot/UnshelveChange";
		String jfile = base + "/Jenkinsfile";

		String baseChange = sampleProject(base, new String[]{"Main"}, "Jenkinsfile");
		assertNotNull(baseChange);

		submitFile(jenkins, "//depot/UnshelveChange/sync/file1", "content");
		String shelveId = shelveFile(jenkins, "//depot/UnshelveChange/unshelve/file2", "content");

		String jFileContent = "pipeline {\n" +
				"  agent any\n" +
				"  stages {\n" +
				"    stage(\"Repro\") {\n" +
				"      steps {\n" +
				"        script {\n" +
				"          p4sync charset: 'none', credential: '" + CREDENTIAL + "',\n" +
				"            populate: autoClean(delete: true, replace: true),\n" +
				"            source: depotSource('//depot/UnshelveChange/sync/...')\n" +
				"          p4unshelve credential: '" + CREDENTIAL + "', resolve: 'at', shelf: '" + shelveId + "',\n" +
				"            workspace: manualSpec(charset: 'none', name: 'jenkins-unshelve-issue-workspace',\n" +
				"            spec: clientSpec(backup: true, clobber: true, line: 'LOCAL', type: 'WRITABLE',\n" +
				"              view: '//depot/UnshelveChange/unshelve/... //jenkins-unshelve-issue-workspace/unshelve/... '))\n" +
				"        }\n" +
				"      }\n" +
				"    }\n" +
				"  }\n" +
				"}";

		submitFile(jenkins, jfile, jFileContent);

		String client = "jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}-unshelve/unshelve";
		String view = base + "/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		scm.getDescriptor().setLastSuccess(true);

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "UnshelveChanges");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);
		QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
		WorkflowRun run1 = build.get();
		Assert.assertNotNull(run1);
		waitForBuild(job, run1.getNumber());

		jenkins.getInstance().reload();
		HtmlPage page = jenkins.createWebClient().getPage(run1, "changes");
		String text = page.asNormalizedText();
		assertTrue(text.contains("Shelved Files:"));
		assertTrue(text.contains("//depot/UnshelveChange/unshelve/file2"));
		assertTrue(text.contains("//depot/UnshelveChange/Jenkinsfile"));
		assertTrue(text.contains("//depot/UnshelveChange/sync/file1"));
	}



	/* ------------------------------------------------------------------------------------------------------------- */
	/*	Helper methods                                                                                               */
	/* ------------------------------------------------------------------------------------------------------------- */

	private CredentialsStore getFolderStore(AbstractFolder f) {
		Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
		CredentialsStore folderStore = null;
		for (CredentialsStore s : stores) {
			if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
				folderStore = s;
				break;
			}
		}
		return folderStore;
	}

	private String sampleProject(String base, String[] branches, String jfile) throws Exception {
		String id = null;
		for (String branch : branches) {
			submitFile(jenkins, base + "/" + branch + "/" + jfile, ""
					+ "pipeline {\n"
					+ "  agent any\n"
					+ "  stages {\n"
					+ "    stage('Test') {\n"
					+ "      steps {\n"
					+ "        script {\n"
					+ "          if(!fileExists('" + jfile + "')) error 'missing " + jfile + "'\n"
					+ "          if(!fileExists('src/fileA'))   error 'missing fileA'\n"
					+ "          if(!fileExists('src/fileB'))   error 'missing fileB'\n"
					+ "        }\n"
					+ "      }\n"
					+ "    }\n"
					+ "  }\n"
					+ "}");
			submitFile(jenkins, base + "/" + branch + "/src/fileA", "content");
			id = submitFile(jenkins, base + "/" + branch + "/src/fileB", "content");
		}
		return id;
	}

	private SwarmHelper sampleSwarmProject(String project, String base, String[] branches) throws Exception {

		SwarmHelper mockSwarm = mock(SwarmHelper.class);
		when(mockSwarm.getBaseUrl()).thenReturn("mock");
		when(mockSwarm.getActiveReviews(project)).thenReturn(new ArrayList<>());

		// Mock Branches and Paths
		List<SwarmProjectAPI.Branch> swarmBranches = new ArrayList<>();
		for (String branch : branches) {
			List<String> swarmMainPath = new ArrayList<>();
			swarmMainPath.add(base + "/" + branch + "/...");
			swarmBranches.add(new SwarmProjectAPI.Branch(branch, branch, swarmMainPath));
		}

		when(mockSwarm.getBranchesInProject(project)).thenReturn(swarmBranches);
		return mockSwarm;
	}

	/**
	 * create the virtual Stream //stream/Ace-virtual, name=Ace-virtual
	 */
	protected static void createVirtualStream(IOptionsServer server) throws P4JavaException {
		String virtualStreamName = "//stream/Ace-virtual";
		String parentName = "//stream/Ace-main";

		// view mapping of "share ..."
		ViewMap<IStreamViewMapping> view = new ViewMap<>();
		Stream.StreamViewMapping sEntry = new Stream.StreamViewMapping();
		sEntry.setPathType(IStreamViewMapping.PathType.SHARE);
		sEntry.setViewPath("...");
		sEntry.setOrder(0);
		view.addEntry(sEntry);

		IStream stream = new Stream();
		stream.setDescription("A simple Virtual Stream");
		stream.setName("Ace-Virtual");
		stream.setParent(parentName);
		stream.setStream(virtualStreamName);
		stream.setOwnerName(server.getUserName());
		stream.setType(IStreamSummary.Type.VIRTUAL);
		stream.setStreamView(view);

		IStreamSummary.IOptions ssOptions = new StreamSummary.Options();
		ssOptions.setNoToParent(true);
		ssOptions.setNoFromParent(true);
		stream.setOptions(ssOptions);

		String result = server.createStream(stream);
	}
}
