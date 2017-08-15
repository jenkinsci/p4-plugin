package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.scm.swarm.P4Path;

import java.io.IOException;
import java.util.logging.Logger;

public class P4Probe extends SCMProbe {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4Probe.class.getName());

	private final P4Head head;

	private transient ClientHelper p4;

	public P4Probe(ClientHelper p4, P4Head head) {
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
			for(P4Path path : head.getPaths()) {
				long change = p4.getHead(path.getPathBuilder("..."));
				if(change > last) {
					last = change;
				}
			}
		} catch (Exception e) {
			logger.warning("Unable to check changes: " + e.getMessage());
		}
		return last;
	}

	@Override
	public SCMProbeStat stat(@NonNull String file) throws IOException {
		try {
			for(P4Path path : head.getPaths()) {
				String depotPath = path.getPathBuilder(file);
				if (p4.hasFile(depotPath)) {
					return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
				}
			}
		} catch (Exception e) {
			throw new IOException("Unable to check file: " + e.getMessage());
		}
		return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
	}

	@Override
	public void close() throws IOException {
		p4.disconnect();
	}
}