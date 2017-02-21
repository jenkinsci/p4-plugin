package org.jenkinsci.plugins.p4.scm;

import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PerforceScmSourceTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-ScmSourceTest-p4root";
	private static P4PasswordImpl auth;

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, VERSION);

	@Before
	public void buildCredentials() throws Exception {
		auth = createCredentials("jenkins", "jenkins", p4d);
	}

	@Test
	public void testMultiBranchWithStreams() throws Exception {

		String credential = auth.getId();

		String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
		String includes = "//streams/...";
		SCMSource source = new StreamsScmSource("streams", credential, includes, null, format, null);

		WorkflowMultiBranchProject multi = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "multi-streams");
		multi.getSCMSourceCriteria(source);

		multi.scheduleBuild();

		// How to get the indexing results?

		// How to get the build result?
	}
}
