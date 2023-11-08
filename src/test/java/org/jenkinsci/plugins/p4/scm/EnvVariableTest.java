package org.jenkinsci.plugins.p4.scm;

import hudson.model.Result;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.ExtendedJenkinsRule;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.changes.P4ChangeSet;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class EnvVariableTest extends DefaultEnvironment {
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
	public void testJenkinsFilePathShouldBeAvailableWhenLightweightAndSkipDefaultCheckoutSet() throws Exception {
		String pipeline = ""
				+ "pipeline {\n"
				+ "  agent any\n"
				+ "  options { skipDefaultCheckout() }\n"
				+ "  stages {\n"
				+ "    stage('Test') {\n"
				+ "      steps {\n"
				+ "        checkout perforce(credential: '" + CREDENTIAL + "',populate: autoClean(quiet: true), workspace: manualSpec(name: 'jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}', spec: clientSpec(view: '//depot/data/... //${P4_CLIENT}/...')))\n"
				+ "        script {\n"
				+ "         echo \"The jenkinsfile path is: ${JENKINSFILE_PATH}\"\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}";

		submitFile(jenkins, "//depot/data/Jenkinsfile", pipeline);

		// Manual workspace spec definition
		String client = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String view = ""
				+ "//depot/data/Jenkinsfile //${P4_CLIENT}/Jenkinsfile" ;


		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);

		// SCM and Populate options
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);

		// SCM Jenkinsfile job
		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "lightWeightWorkflow");
		CpsScmFlowDefinition cpsScmFlowDefinition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
		cpsScmFlowDefinition.setLightweight(true);
		job.setDefinition(cpsScmFlowDefinition);
		jenkins.assertLogContains("The jenkinsfile path is: //depot/data/Jenkinsfile",job.scheduleBuild2(0).get());
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
}
