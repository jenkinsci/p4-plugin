package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.TempClientHelper;

import java.io.IOException;
import java.io.Serial;
import java.util.logging.Logger;

public class P4SCMProbe extends SCMProbe {

	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void addAliases() {
		Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.p4.scm.P4Probe", P4SCMProbe.class);
	}

	@Serial
	private static final long serialVersionUID = 1L;

	private final static Logger logger = Logger.getLogger(P4SCMProbe.class.getName());

	private final P4SCMHead head;
	private transient TempClientHelper p4 = null;

	public P4SCMProbe(TempClientHelper p4, P4SCMHead head) {
		this.head = head;
		this.p4 = p4;
	}

	@Override
	public String name() {
		return head.getName();
	}

	@Override
	public long lastModified() {
		long last = 0L;
		try {
			// use temp workspace and client syntax to get changes
			long change = p4.getClientHead();
			if (change > last) {
				last = change;
			}
		} catch (Exception e) {
			logger.warning("Unable to check changes: " + e.getMessage());
		}
		return last;
	}

	@NonNull
    @Override
	public SCMProbeStat stat(@NonNull String file) throws IOException {
		try {
			P4Path path = head.getPath();
			String filePath = path.getPathBuilder(file); // Depot Path syntax

			// When probing Streams, switch to use client path syntax.  This works for
			// all streams, including virtual streams(JENKINS-62699).
			p4.log("Scanning for " + filePath);
			String clientStream = p4.getClient().getStream();
			if ( clientStream != null ) {
				filePath = filePath.replaceFirst(clientStream, "//" + p4.getClientUUID());
			}

			if (p4.hasFile(filePath)) {
				return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
			}
		} catch (Exception e) {
			throw new IOException("Unable to check file: " + e.getMessage());
		}
		return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
	}

	@Override
	public void close() {
		// No need to close ConnectionHelper
	}
}