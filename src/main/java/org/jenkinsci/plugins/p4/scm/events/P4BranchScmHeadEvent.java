package org.jenkinsci.plugins.p4.scm.events;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.AbstractP4SCMSource;
import org.jenkinsci.plugins.p4.scm.P4SCMRevision;

import java.util.Collections;
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

		// Verify SCMSource
		if (!(scmSource instanceof AbstractP4SCMSource)) {
			// Not a Perforce Source
			return Collections.emptyMap();
		}
		AbstractP4SCMSource source = (AbstractP4SCMSource) scmSource;

		// Check Perforce server P4PORT
		String p4port = getField(getPayload(), ReviewProp.P4PORT);
		String id = source.getCredential();
		P4BaseCredentials credential = ConnectionHelper.findCredential(id, scmSource.getOwner());
		if (p4port == null || !p4port.equals(credential.getP4port())) {
			return Collections.emptyMap();
		}

		P4SCMRevision revision = source.getRevision(getPayload());
		if(revision == null) {
			return Collections.emptyMap();
		}

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

	private static String getField(JSONObject payload, ReviewProp prop) {
		return payload.getString(prop.getProp());
	}
}
