package org.jenkinsci.plugins.p4.review;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class ReviewImplTest extends DefaultEnvironment {

	private final String credential = "id";
	private final static String P4PORT = "localhost:1999";

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@Test
	public void testStaticReviewImpl() throws Exception {

		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM,
				credential, "desc", P4PORT, null, "jenkins", "0", "0", null, "jenkins");
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();

		String client = defaultClient();
		FreeStyleProject project = jenkins
				.createFreeStyleProject("StaticReview");
		Workspace workspace = new StaticWorkspaceImpl("none", false, client);
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(credential, workspace, populate);
		project.setScm(scm);
		project.save();

		HtmlPage page = jenkins.createWebClient().getPage(project, "review");
		HtmlElement review = page.getElementByName("review");
		assertEquals(review.getAttribute("type"), "text");
	}
}
