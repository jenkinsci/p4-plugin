package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;

import java.io.IOException;
import java.util.logging.Logger;

public class P4SCMProbe extends SCMProbe {

	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void addAliases() {
		Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.p4.scm.P4Probe", P4SCMProbe.class);
	}

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4SCMProbe.class.getName());

	private final P4SCMHead head;

	private transient ConnectionHelper p4;

	public P4SCMProbe(ConnectionHelper p4, P4SCMHead head) {
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
			P4Path path = head.getPath();
			long change = p4.getHead(path.getPathBuilder("..."));
			if (change > last) {
				last = change;
			}
		} catch (Exception e) {
			logger.warning("Unable to check changes: " + e.getMessage());
		}
		return last;
	}

	@Override
	public SCMProbeStat stat(@NonNull String file) throws IOException {
		try {
			P4Path path = head.getPath();
			String depotPath = path.getPathBuilder(file);
			if (p4.hasFile(depotPath)) {
				return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
			}
		} catch (Exception e) {
			throw new IOException("Unable to check file: " + e.getMessage());
		}
		return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
	}

	@Override
	public void close() throws IOException {
		// No need to close ConnectionHelper
	}
}