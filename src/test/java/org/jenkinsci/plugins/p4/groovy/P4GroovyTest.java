package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Map;

public class P4GroovyTest extends DefaultEnvironment {
	private static final String P4ROOT = "tmp-P4GroovyTest-p4root";

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Test
	public void testInvalidCredentials() {
		StreamWorkspaceImpl ws = new StreamWorkspaceImpl(null, false, "//stream/main", "job1-temp-branch1");
		P4Groovy p4 = new P4Groovy("bad credential", null, null, ws, new FilePath(new File("workspace")));
		try {
			Map<String, Object>[] result = p4.run("status");
		} catch (Exception e) {
			Assert.assertTrue(e.getMessage().contains("Invalid credentials"));
			e.printStackTrace();
		}
	}
}
