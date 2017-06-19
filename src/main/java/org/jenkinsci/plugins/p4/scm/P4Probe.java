package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class P4Probe extends SCMProbe {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4Probe.class.getName());

	private final String name;
	private final List<String> paths;

	private transient ClientHelper p4;

	public P4Probe(ClientHelper p4, P4Head head) {
		this.name = head.getName();
		this.paths = head.getPaths();
		this.p4 = p4;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public long lastModified() {
		long last = 0L;
		try {
			for(String path : paths) {
				long change = p4.getHead(path + "/...");
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
			for(String path : paths) {
				String depotPath = path + "/" + file;
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