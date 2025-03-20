package org.jenkinsci.plugins.p4.tagging;

import com.perforce.p4java.core.ILabel;
import com.perforce.p4java.core.ILabelMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static com.perforce.p4java.core.IMapEntry.EntryType.EXCLUDE;
import static com.perforce.p4java.core.IMapEntry.EntryType.INCLUDE;
import static org.junit.Assert.assertEquals;

public class TaggingTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-FreeStyleTest-p4root";

	@ClassRule
	public static JenkinsRule jenkins = new JenkinsRule();

	@Rule
	public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

	@Before
	public void buildCredentials() throws IOException {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	public void labelShouldGetCreatedByUsingLabelInPostBuild() throws Exception {
		FreeStyleProject project = jenkins.createFreeStyleProject("LabelProject");
		String view = """
				//depot/Freestyle/... //${P4_CLIENT}/Freestyle/...
				-//depot/Freestyle/main/sub1/... //${P4_CLIENT}/Freestyle/main/sub1/...
				-//depot/Freestyle/main/sub2/... //${P4_CLIENT}/Freestyle/main/sub2/...
				-//depot/Freestyle/main/sub3/... //${P4_CLIENT}/Freestyle/main/sub3/...
				//depot/sub/... //${P4_CLIENT}/sub/...
				"//depot/sub sub/..." "//${P4_CLIENT}/sub sub/...\"""";

		WorkspaceSpec spec = new WorkspaceSpec(false, true, false, false, false, false, null, "LOCAL", view, null, null, null, false);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, "jenkins-${NODE_NAME}-${JOB_NAME}.ws", spec, false);

		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
		project.setScm(scm);

		TagNotifier tagNotifier = new TagNotifier("Label-1", "Perforce Test Label", false);
		project.getPublishersList().add(tagNotifier);
		project.save();

		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertEquals(Result.SUCCESS, build.getResult());
		jenkins.assertLogContains("Label Label-1 saved.", build);

		ViewMap<ILabelMapping> viewMapping;
		try (ConnectionHelper p4 = new ConnectionHelper(project, CREDENTIAL, null)) {
			IOptionsServer server = p4.getConnection();
			ILabel label = server.getLabel("Label-1");
			viewMapping = label.getViewMapping();
		}

		assertEquals(INCLUDE, viewMapping.getEntry(0).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(1).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(2).getType());
		assertEquals(EXCLUDE, viewMapping.getEntry(3).getType());
		assertEquals(INCLUDE, viewMapping.getEntry(4).getType());
		assertEquals(INCLUDE, viewMapping.getEntry(5).getType());
	}
}
