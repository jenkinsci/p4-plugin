package org.jenkinsci.plugins.p4.scm.events;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.AbstractP4SCMSource;
import org.jenkinsci.plugins.p4.scm.BranchesSCMSource;
import org.jenkinsci.plugins.p4.scm.P4SCMHead;
import org.jenkinsci.plugins.p4.scm.P4Path;
import org.jenkinsci.plugins.p4.scm.P4SCMRevision;
import org.jenkinsci.plugins.p4.scm.StreamsSCMSource;
import org.jenkinsci.plugins.p4.scm.SwarmSCMSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class P4BranchSCMHeadEvent extends SCMHeadEvent<JSONObject> {

	public P4BranchSCMHeadEvent(@NonNull Type type, JSONObject payload, String origin) {
		super(type, payload, origin);
	}

	@NonNull
	@Override
	public String getSourceName() {
		String p4port = getField(getPayload(), ReviewProp.P4PORT);
		String change = getField(getPayload(), ReviewProp.CHANGE);
		return p4port + "/" + change;
	}

	@NonNull
	@Override
	public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource scmSource) {

		// Check Perforce server P4PORT
		if (scmSource instanceof AbstractP4SCMSource) {
			AbstractP4SCMSource p4ScmSource = (AbstractP4SCMSource) scmSource;
			String p4port = getField(getPayload(), ReviewProp.P4PORT);
			String id = p4ScmSource.getCredential();
			P4BaseCredentials credential = ConnectionHelper.findCredential(id, scmSource.getOwner());
			if (p4port == null || !p4port.equals(credential.getP4port())) {
				return Collections.emptyMap();
			}
		} else {
			// Not a Perforce Source
			return Collections.emptyMap();
		}

		// Check Swarm Sources
		if (scmSource instanceof SwarmSCMSource) {
			SwarmSCMSource swarmSource = (SwarmSCMSource) scmSource;

			// Check matching Swarm project name
			String project = getField(getPayload(), ReviewProp.PROJECT);
			if (project == null || !project.equals(swarmSource.getProject())) {
				return Collections.emptyMap();
			}
		}

		// Check Branch Sources
		if (scmSource instanceof BranchesSCMSource) {
			BranchesSCMSource branchSource = (BranchesSCMSource) scmSource;

			// Check matching Project path included in Source
			String project = getField(getPayload(), ReviewProp.PROJECT);
			if (project == null || !findInclude(project, branchSource)) {
				return Collections.emptyMap();
			}

			// TODO get Jenkinsfile name from source
			//String jenkinsfile = branchSource.getScriptPathOrDefault();

			// TODO get file from change (p4 describe) and walk up path tree looking for Jenkinsfile (p4 files //PATH/L1/L2/...Jenkinsfile) remaining path will give branch and project.
			//ClientHelper p4 = new ClientHelper(branchSource.getOwner(), branchSource.getCredential(), null, null);

			// TODO if found then candidate

		}

		// Check Stream Sources
		if (scmSource instanceof StreamsSCMSource) {
			StreamsSCMSource streamSource = (StreamsSCMSource) scmSource;

			// Check matching Project path included in Stream
			String project = getField(getPayload(), ReviewProp.PROJECT);
			if (project == null || !findInclude(project, streamSource)) {
				return Collections.emptyMap();
			}
		}

		P4SCMRevision revision = parsePayload(getPayload());

		return Collections.singletonMap(revision.getHead(), revision);
	}

	@Override
	public boolean isMatch(@NonNull SCM scm) {
		return true;
	}

	@Override
	public boolean isMatch(@NonNull SCMNavigator scmNavigator) {
		return false;
	}

	public static P4SCMRevision parsePayload(JSONObject payload) {
		String branch = getField(payload, ReviewProp.BRANCH);
		String change = getField(payload, ReviewProp.CHANGE);
		String path = getField(payload, ReviewProp.PATH);
		P4Path p4path = new P4Path(path, String.valueOf(change));
		P4SCMHead head = new P4SCMHead(branch, p4path);

		P4Ref ref = new P4ChangeRef(Long.parseLong(change));
		P4SCMRevision revision = new P4SCMRevision(head, ref);
		return revision;
	}

	private static String getField(JSONObject payload, ReviewProp prop) {
		return payload.getString(prop.getProp());
	}

	private boolean findInclude(String project, AbstractP4SCMSource source) {

		project = (project.endsWith("/*")) ? project.substring(0, project.lastIndexOf("/*")) : project;
		project = (project.endsWith("/...")) ? project.substring(0, project.lastIndexOf("/...")) : project;

		List<String> includes = source.getIncludePaths();
		for (String i : includes) {
			if (i.startsWith(project)) {
				return true;
			}
		}
		return false;
	}
}
