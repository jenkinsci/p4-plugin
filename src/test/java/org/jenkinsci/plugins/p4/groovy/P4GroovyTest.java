package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class P4GroovyTest extends DefaultEnvironment {

    private static final String P4ROOT = "tmp-P4GroovyTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        jenkins = rule;
    }

	@Test
	void testInvalidCredentials() {
		StreamWorkspaceImpl ws = new StreamWorkspaceImpl(null, false, "//stream/main", "job1-temp-branch1");
		P4Groovy p4 = new P4Groovy(null, null, ws, new FilePath(new File("workspace")));
		try {
			Map<String, Object>[] result = p4.run("status");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Invalid credentials"));
			e.printStackTrace();
		}
	}
}
