package org.jenkinsci.plugins.p4.client;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerRule;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.review.SafeParametersAction;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class CheckoutTest extends DefaultEnvironment {
    
    private static final Logger logger = Logger.getLogger(CheckoutTest.class.getName());
    private static final String P4ROOT = "tmp-CheckoutTest-p4root";
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public SampleServerRule p4d = new SampleServerRule(P4ROOT, R15_1);

    @Before
    public void buildCredentials() throws Exception {
	createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
    }
    
    @Test
    public void testCheckoutUnrestrictedView() throws Exception {
        String client = "CheckoutUnrestrictedView.ws";
        String view = "//depot/... //" + client + "/...";
	WorkspaceSpec spec = new WorkspaceSpec(view, null);

	FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutUnrestrictedView");
	ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
        
        Populate populate = new AutoCleanImpl(true, true, false, false, false, "", null);
	PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
	project.setScm(scm);
	project.save();
        
        // Request change 17 and expect change 17 as it's included in the workspace view
        final String requestedChangelist = "17";
        final String expectedChangelist = requestedChangelist;

	List<ParameterValue> list = new ArrayList<>();
	list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
	list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), requestedChangelist));
	Action actions = new SafeParametersAction(new ArrayList<>(), list);

	FreeStyleBuild build;
	Cause.UserIdCause cause = new Cause.UserIdCause();
	build = project.scheduleBuild2(0, cause, actions).get();
	assertEquals(Result.SUCCESS, build.getResult());

        Map<String, String> env = new HashMap<>();
        scm.buildEnvVars(build, env);
        
        // Assert that the workspace sync'd the expected change
	assertEquals(env.get("P4_CHANGELIST"), expectedChangelist);
    }
    
    @Issue("JENKINS-57534")
    @Ignore 
    @Test
    public void testCheckoutRestrictedView() throws Exception {
        String client = "CheckoutRestrictedView.ws";
        String view = "//depot/Main/... //" + client + "/Main/...";
	WorkspaceSpec spec = new WorkspaceSpec(view, null);

	FreeStyleProject project = jenkins.createFreeStyleProject("CheckoutRestrictedView");
	ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec);
        
        Populate populate = new AutoCleanImpl(true, true, false, false, false, "", null);
	PerforceScm scm = new PerforceScm(CREDENTIAL, workspace, populate);
	project.setScm(scm);
	project.save();

	// Request change 17, but expect change 16 as change 17 is not included in the workspace view
        final String requestedChangelist = "17";
        final String expectedChangelist = "16";

	List<ParameterValue> list = new ArrayList<>();
	list.add(new StringParameterValue(ReviewProp.SWARM_STATUS.toString(), "submitted"));
	list.add(new StringParameterValue(ReviewProp.P4_CHANGE.toString(), requestedChangelist));
	Action actions = new SafeParametersAction(new ArrayList<>(), list);

	FreeStyleBuild build;
	Cause.UserIdCause cause = new Cause.UserIdCause();
	build = project.scheduleBuild2(0, cause, actions).get();
	assertEquals(Result.SUCCESS, build.getResult());

        Map<String, String> env = new HashMap<>();
        scm.buildEnvVars(build, env);
        
        // Assert that the workspace sync'd the expected change
	assertEquals(env.get("P4_CHANGELIST"), expectedChangelist);
    }
}
