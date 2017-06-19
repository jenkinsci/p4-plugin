package org.jenkinsci.plugins.p4.scm;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Result;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class PerforceScmSourceTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ScmSourceTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
	}

	@Test
	public void testMultiBranchWithStreams() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new StreamsScmSource("streams", credential, includes, null, format);

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

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/...";
		SCMSource source = new BranchesScmSource("classic", credential, includes, null, format);

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

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//depot/...";
		SCMSource source = new StreamsScmSource("no-streams", credential, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "no-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We have no branches", multi.getItems(), containsInAnyOrder());
	}

	@Test
	public void testSimplePathStreams() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream";
		SCMSource source = new StreamsScmSource("path-streams", credential, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "path-streams");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testSimplePathClassic() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream";
		SCMSource source = new BranchesScmSource("path-classic", credential, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "path-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testStarPathClassic() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//stream/*";
		SCMSource source = new BranchesScmSource("star-classic", credential, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "star-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testRootPathClassic() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//...";
		SCMSource source = new BranchesScmSource("root-classic", credential, includes, null, format);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "root-classic");
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

	@Test
	public void testRootPathStreams() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//...";
		SCMSource source = new StreamsScmSource("root-streams", credential, includes, null, format);

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
		SCMSource source = new BranchesScmSource("classic", inFolderCredentials.getId(), includes, null, format);
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
		SCMSource source = new StreamsScmSource("streams", inFolderCredentials.getId(), includes, null, format);
		multi.getSourcesList().add(new BranchSource(source));
		multi.scheduleBuild2(0);
		jenkins.waitUntilNoActivity();

		assertEquals("Branch Indexing succeeded", Result.SUCCESS, multi.getComputation().getResult());
		assertThat("We now have branches", multi.getItems(), not(containsInAnyOrder()));
	}

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
}
