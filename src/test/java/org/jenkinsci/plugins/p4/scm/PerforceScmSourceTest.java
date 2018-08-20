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
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.scm.events.P4BranchScmHeadEvent;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashMap;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class PerforceScmSourceTest extends DefaultEnvironment {

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
				+ "          if(!fileExists('Jenkinsfile'))         error 'missing Jenkinsfile'\n"
				+ "          if(!fileExists('depot/classic/A/src/fileA'))   error 'missing fileA'\n"
				+ "          if(!fileExists('depot/classic/A/tests/fileB')) error 'missing fileB'\n"
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
		sampleProject(base, new String[]{"Main"});

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
		String baseChange = sampleProject(base, new String[]{"Main", "Dev"});
		assertNotNull(baseChange);

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = base + "/...";
		BranchesScmSource source = new BranchesScmSource(CREDENTIAL, includes, null, format);
		source.setPopulate(new AutoCleanImpl());

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-update-event");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);

		jenkins.waitUntilNoActivity();
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));

		// Test on branch 'Main'
		String branch = "Main";
		WorkflowJob job = multi.getItem(branch);
		assertThat("We now have a branch", job, notNullValue());

		// Make a change
		String change = submitFile(jenkins, base + "/" + branch + "/src/fileA", "edit");

		HashMap<String, String> map = new HashMap<>();
		map.put("p4port", p4d.getRshPort());
		map.put("project", base);
		map.put("change", change);
		map.put("branch", branch);
		map.put("path", base + "/" + branch);
		JSONObject payload = JSONObject.fromObject(map);

		String origin = "testMultiBranchClassicUpdateEvent";
		P4BranchScmHeadEvent event = new P4BranchScmHeadEvent(SCMEvent.Type.UPDATED, payload, origin);
		SCMHeadEvent.fireNow(event);

		Thread.sleep(500);
		jenkins.waitUntilNoActivity();

		WorkflowRun build = multi.getItem("Main").getLastBuild();
		assertTrue("Main has built", build.number == 2);
		assertTrue("Dev has not built", multi.getItem("Dev").getLastBuild().number == 1);
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

	private String sampleProject(String base, String[] branches) throws Exception {
		String id = null;
		for (String branch : branches) {
			submitFile(jenkins, base + "/" + branch + "/Jenkinsfile", ""
					+ "pipeline {\n"
					+ "  agent any\n"
					+ "  stages {\n"
					+ "    stage('Test') {\n"
					+ "      steps {\n"
					+ "        script {\n"
					+ "          if(!fileExists('Jenkinsfile')) error 'missing Jenkinsfile'\n"
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
}
