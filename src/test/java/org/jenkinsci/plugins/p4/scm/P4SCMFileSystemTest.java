package org.jenkinsci.plugins.p4.scm;

import hudson.model.AutoCompletionCandidates;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.NavigateHelper;
import org.jenkinsci.plugins.p4.client.TempClientHelper;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class P4SCMFileSystemTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ScmFileSystemTest-p4root";

	private static P4PasswordImpl auth;
	private static ManualWorkspaceImpl workspace;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);

		String client = "testNavigation.ws";
		String view = "//depot/... //${P4_CLIENT}/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		workspace = new ManualWorkspaceImpl("none", false, client, spec);
		workspace.setExpand(new HashMap<String, String>());
	}

	@Test
	public void testAutoComplete() {
		new ConnectionHelper(auth); // Initialise default connection
		NavigateHelper nav = new NavigateHelper(5);

		AutoCompletionCandidates results = nav.getCandidates("//");
		assertNotNull(results);
		assertEquals(2, results.getValues().size());

		results = nav.getCandidates("//de");
		assertNotNull(results);
		assertEquals("//depot/", results.getValues().get(0));

		results = nav.getCandidates("//depot/");
		assertNotNull(results);
		assertEquals("//depot/Data/", results.getValues().get(0));

		results = nav.getCandidates("//depot/Data/");
		assertNotNull(results);
		assertEquals("//depot/Data/file-0.dat", results.getValues().get(0));
	}

	@Test
	public void testNodes() throws Exception {
		String credential = auth.getId();
		TempClientHelper p4 = new TempClientHelper(null, credential, null, workspace);
		p4.setClient(workspace);
		NavigateHelper nav = new NavigateHelper(p4.getConnection());

		List<NavigateHelper.Node> results = nav.getNodes("");
		assertNotNull(results);
		assertEquals(2, results.size());
		assertEquals("//depot/Data/", results.get(0).getDepotPath());
		assertEquals("Data", results.get(0).getName());

		results = nav.getNodes("Data");
		assertNotNull(results);
		assertEquals("//depot/Data/file-0.dat", results.get(0).getDepotPath());
		assertEquals("file-0.dat", results.get(0).getName());
	}

	@Test
	public void ofSource_Smokes() throws Exception {

		String id = auth.getId();
		String format = workspace.getName();

		BranchesScmSource source = new BranchesScmSource(id, "//depot/...", null, format);
		source.setFilter(BranchesScmSource.DescriptorImpl.defaultFilter);
		source.setMappings(BranchesScmSource.DescriptorImpl.defaultPath);

		SCMSourceOwner owner = new WorkflowMultiBranchProject(Jenkins.getInstance(), "multi1");
		source.setOwner(owner);

		P4Path path = new P4Path("//depot");
		P4Head head = new P4Head("main", Arrays.asList(path));

		SCMFileSystem fs = SCMFileSystem.of(source, head);
		assertThat(fs, notNullValue());
		SCMFile root = fs.getRoot();
		assertThat(root, notNullValue());
		assertTrue(root.isRoot());
		Iterable<SCMFile> children = root.children();
		Iterator<SCMFile> iterator = children.iterator();
		assertThat(iterator.hasNext(), is(true));

		// location: Data/
		SCMFile dir = iterator.next();
		assertThat(dir.getName(), is("Data"));
		children = dir.children();
		iterator = children.iterator();
		assertThat(iterator.hasNext(), is(true));

		// location: Data/file-0.dat
		SCMFile file = iterator.next();
		assertThat(file.getName(), is("file-0.dat"));

		file = fs.getRoot().child("Main").child("file-12.txt");
		assertThat(file.getName(), is("file-12.txt"));
		assertTrue(file.contentAsString().startsWith("filename: file-12.txt"));
	}

	@Test
	public void testLightWeightWorkflow() throws Exception {

		String content = ""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content);
		submitFile(jenkins, "//depot/Data/j001", "Content");

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String view = ""
				+ "//depot/Data/... //" + client + "/data/..." + "\n"
				+ "//depot/Main/... //" + client + "/main/...";

		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "lightWeightWorkflow");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void testLightWeightP4_CLIENT() throws Exception {

		String content = ""
				+ "node {\n"
				+ "   p4sync credential: '" + CREDENTIAL + "', template: 'test.ws'\n"
				+ "   println \"P4_CHANGELIST: ${env.P4_CHANGELIST}\"\n"
				+ "}";

		submitFile(jenkins, "//depot/Data/Jenkinsfile", content);
		submitFile(jenkins, "//depot/Data/j001", "Content");

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String view = ""
				+ "//depot/Data/... //${P4_CLIENT}/data/..." + "\n"
				+ "//depot/Main/... //${P4_CLIENT}/main/...";

		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "lightWeightWorkflow");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);

		jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}
}
