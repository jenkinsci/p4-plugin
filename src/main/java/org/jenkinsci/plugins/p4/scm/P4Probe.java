package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.IOException;
import java.util.logging.Logger;

public class P4Probe extends SCMProbe {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4Probe.class.getName());

	private final String base;
	transient private final ClientHelper p4;

	public P4Probe(final ClientHelper p4, final String base) {
		this.base = base;
		this.p4 = p4;
	}

	@Override
	public String name() {
		return base;
	}

	@Override
	public long lastModified() {
		try {
			return p4.getHead(base + "/...");
		} catch (Exception e) {
			logger.warning("Unable to check changes: " + e.getMessage());
		}
		return 0;
	}

	@NonNull
	@Override
	public SCMProbeStat stat(@NonNull String path) throws IOException {
		try {
			String depotPath = base + "/" + path;
			if (p4.hasFile(depotPath)) {
				return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
			}
		} catch (Exception e) {
			logger.warning("Unable to check file: " + e.getMessage());
		}
		return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
	}

	@Override
	public void close() throws IOException {
		p4.disconnect();
	}
}