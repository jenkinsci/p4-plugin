package org.jenkinsci.plugins.p4.scm;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Result;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.events.P4BranchSCMHeadEvent;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmHelper;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmProjectAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewAPI;
import org.jenkinsci.plugins.p4.swarmAPI.SwarmReviewsAPI;
import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PerforceSCMSourceTest extends DefaultEnvironment {

	private static Logger logger = Logger.getLogger(PerforceSCMSourceTest.class.getName());

	private static final String P4ROOT = "tmp-ScmSourceTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

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
		SCMSource source = new StreamsScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-streams");
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
	public void testSimplePathClassic() throws Exception {

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream";
		SCMSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "path-classic");
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
		submitFile(jenkins, "//depot/classic/A/Jenkinsfile", ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "          if(!fileExists('Jenkinsfile')) error 'missing Jenkinsfile'\n"
				+ "          if(!fileExists('fileA'))       error 'missing fileA'\n"
				+ "          if(!fileExists('fileB'))       error 'missing fileB'\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");

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

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		WorkflowRun build = multi.getItem("Main").getLastBuild();
		assertEquals("Main has built", 2, build.number);
		assertTrue("Dev has not built", multi.getItem("Dev").getLastBuild().number == 1);
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

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		WorkflowRun runMain = multi.getItem("Main").getLastBuild();
		assertEquals("Main has built", 2, runMain.number);
		assertEquals(Result.SUCCESS, runMain.getResult());

		assertTrue("Dev has not built", multi.getItem("Dev").getLastBuild().number == 1);
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
		projects.put(project, Arrays.asList("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects);
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(mockReview));

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

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertTrue("Dev should not build", multi.getItem("Dev").getLastBuild().number == 1);

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
		projects.put(project, Arrays.asList("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects);
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(mockReview));

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

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertTrue("Dev should not build", multi.getItem("Dev").getLastBuild().number == 1);

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
		projects.put(project, Arrays.asList("Main"));
		SwarmReviewAPI.Review mockReview = new SwarmReviewAPI.Review(changes, changes, projects);
		when(mockSwarm.getSwarmReview(anyString())).thenReturn(new SwarmReviewAPI(mockReview));

		List<SwarmReviewsAPI.Reviews> mockReviewsList = new ArrayList<>();
		SwarmReviewsAPI.Reviews mockReviews = new SwarmReviewsAPI.Reviews(Long.parseLong(review), changes);
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

		TimeUnit.SECONDS.sleep(job.getQuietPeriod());
		jenkins.waitUntilNoActivity();

		assertTrue("Dev should not built", multi.getItem("Dev").getLastBuild().number == 1);
		assertTrue("Main should not built", multi.getItem("Main").getLastBuild().number == 1);

		WorkflowJob revJob = multi.getItem(review);
		assertNotNull(revJob);

		WorkflowRun revRun = revJob.getLastBuild();
		assertNotNull(revJob);

		jenkins.assertLogContains("P4 Task: syncing files at change: " + review, revRun);
		jenkins.assertLogContains("P4 Task: unshelve review: " + review, revRun);
		assertEquals(Result.SUCCESS, revRun.getResult());
	}

	@Test
	@Issue("JENKINS-54382")
	public void testMultiBranchDeepJenkinsfile() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/deep";
		String scriptPath = "space build/jfile";
		String baseChange = sampleProject(base, new String[]{"Main", "Dev"}, scriptPath);
		assertNotNull(baseChange);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "deep-jenkinsfile");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
	}

	@Test
	public void testMultiBranchMultiLineDeepJenkinsfile() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/mdeep";
		String scriptPath = "space build/jfile";
		submitFile(jenkins, "//depot/other/src/fileA", "content");
		String baseChange = sampleProject(base, new String[]{"Main", "Dev"}, scriptPath);
		assertNotNull(baseChange);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/..." + "\n" + "//depot/other/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-line-deep-jenkinsfile");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
	}

	@Test
	public void testJenkinsfilePathAvailableAsEnvVar() throws Exception {
		String base = "//depot/default/default1";
		String scriptPath = "build/MyJenkinsfile";
		String branch = "Main";
		submitFile(jenkins, base + "/" + branch + "/" + scriptPath, ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "             echo \"The jenkinsfile path is: ${JENKINSFILE_PATH}\""
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.createProject(WorkflowMultiBranchProject.class, "JenkinsfilePathEnvVar");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		WorkflowJob job = multi.getItem("Main");
		WorkflowRun build = job.getLastBuild();

		assertThat("The branch was built", build, notNullValue());
		assertEquals(Result.SUCCESS, build.getResult());
		jenkins.assertLogContains("The jenkinsfile path is: " + base + "/" + branch + "/" + scriptPath, build);
	}

	@Test
	public void testMultiBranchRemoteJenkinsfileScanPerChange() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/Remote";
		String scriptPath = "a space/jfile";
		String branch = "Main";

		// Remote Jenkinsfile
		submitFile(jenkins, base + "/" + branch + "/" + scriptPath, ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "          if(!fileExists('" + scriptPath + "')) error 'missing " + scriptPath + "'\n"
				+ "          if(!fileExists('src/fileA'))   error 'missing fileA'\n"
				+ "          if(!fileExists('src/fileB'))   error 'missing fileB'\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");

		// Source files
		String projBase = "//depot/ProjectA/Main";
		submitFile(jenkins, projBase + "/src/fileA", "content");
		String baseChange = submitFile(jenkins, projBase + "/src/fileB", "content");
		assertNotNull(baseChange);

		// Setup MultiBranch
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		List<Filter> filter = new ArrayList<>();
		filter.add(new FilterPerChangeImpl(true));
		source.setFilter(filter);
		source.setPopulate(new AutoCleanImpl());
		source.setMappings("//depot/ProjectA/${BRANCH_NAME}/...");

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-remote-scan-jenkinsfile");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		P4ChangeSet baseSet = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(baseChange, baseSet.getHistory().get(0).getId().toString());

		// Multiple changes
		String change1 = submitFile(jenkins, projBase + "/src/fileC", "content");
		String change2 = submitFile(jenkins, projBase + "/src/fileD", "content");

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		assertEquals(1, job.getLastBuild().getChangeSets().size());
		P4ChangeSet changeSet1 = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(1, changeSet1.getHistory().size());
		assertEquals(change1, changeSet1.getHistory().get(0).getId().toString());

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		assertEquals(1, job.getLastBuild().getChangeSets().size());
		P4ChangeSet changeSet2 = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(1, changeSet2.getHistory().size());
		assertEquals(change2, changeSet2.getHistory().get(0).getId().toString());
	}

	@Test
	public void testMultiBranchRemoteJenkinsfileLatestChange() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/LatestRemote";
		String scriptPath = "a space/jfile";
		String branch = "Main";

		// Remote Jenkinsfile
		submitFile(jenkins, base + "/" + branch + "/" + scriptPath, ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "          if(!fileExists('" + scriptPath + "')) error 'missing " + scriptPath + "'\n"
				+ "          if(!fileExists('src/fileA'))   error 'missing fileA'\n"
				+ "          if(!fileExists('src/fileB'))   error 'missing fileB'\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");

		// Source files
		String projBase = "//depot/ProjectB/Main";
		submitFile(jenkins, projBase + "/src/fileA", "content");
		String baseChange = submitFile(jenkins, projBase + "/src/fileB", "content");
		assertNotNull(baseChange);

		// Setup MultiBranch
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());
		source.setMappings("//depot/ProjectB/${BRANCH_NAME}/...");

		// Empty set of filters
		List<Filter> filter = new ArrayList<>();
		source.setFilter(filter);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-remote-latest-jenkinsfile");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		P4ChangeSet baseSet = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(baseChange, baseSet.getHistory().get(0).getId().toString());

		// Multiple changes
		submitFile(jenkins, projBase + "/src/fileC", "content");
		String change2 = submitFile(jenkins, projBase + "/src/fileD", "content");

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		assertEquals(1, job.getLastBuild().getChangeSets().size());
		P4ChangeSet changeSet = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(2, changeSet.getHistory().size());
		assertEquals(change2, changeSet.getHistory().get(0).getId().toString());
	}

	@Test
	public void testMultiBranchRemoteJenkinsfilePlus() throws Exception {

		// Setup sample Multi Branch Project
		String base = "//depot/Plus";
		String scriptPath = "a space/jfile";
		String branch = "Main";

		// Remote Jenkinsfile with lightweight access to extra files
		submitFile(jenkins, base + "/" + branch + "/" + scriptPath, ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        script {\n"
				+ "          if(!fileExists('" + scriptPath + "')) error 'missing " + scriptPath + "'\n"
				+ "          if(!fileExists('test.yaml'))   error 'missing test.yaml'\n"
				+ "          if(!fileExists('depot/test_Main/ProjectC/src/fileA'))   error 'missing fileA'\n"
				+ "          if(!fileExists('depot/test_Main/ProjectC/src/fileB'))   error 'missing fileB'\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}");

		// Extra file (Kubernetes)
		submitFile(jenkins, base + "/" + branch + "/test.yaml", "content");

		// Source files
		String projBase = "//depot/test_Main/ProjectC";
		submitFile(jenkins, projBase + "/src/fileA", "content");
		String baseChange = submitFile(jenkins, projBase + "/src/fileB", "content");
		assertNotNull(baseChange);

		// Setup MultiBranch with remote and local mappings
		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		List<Filter> filter = new ArrayList<>();
		filter.add(new FilterPerChangeImpl(true));
		source.setFilter(filter);
		source.setPopulate(new AutoCleanImpl());
		source.setMappings("//depot/test_${BRANCH_NAME}/ProjectC/...\n...");

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-remote-plus-jenkinsfile");
		multi.getSourcesList().add(new BranchSource(source));

		WorkflowBranchProjectFactory workflowBranchProjectFactory = new WorkflowBranchProjectFactory();
		workflowBranchProjectFactory.setScriptPath(scriptPath);
		multi.setProjectFactory(workflowBranchProjectFactory);

		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		P4ChangeSet baseSet = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(baseChange, baseSet.getHistory().get(0).getId().toString());
		/*
		Test to confirm PollPerChange works with the latest changes
		 */
		//Make change to fileA and submit
		String change1 = submitFile(jenkins, projBase + "/src/fileA", "content changed");
		//Make change to fileB and submit
		String change2 = submitFile(jenkins, projBase + "/src/fileB", "content changed");
		//Schedule build
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
		// Test on branch 'Main'
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		P4ChangeSet changes1 = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(change1, changes1.getHistory().get(0).getId().toString());

		//Schedule another build to build the next change
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
		// Test on branch 'Main'
		assertThat("We now have a branch", job, notNullValue());
		assertEquals(Result.SUCCESS, job.getLastBuild().getResult());
		P4ChangeSet changes2 = (P4ChangeSet) job.getLastBuild().getChangeSets().get(0);
		assertEquals(change2, changes2.getHistory().get(0).getId().toString());
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
}
