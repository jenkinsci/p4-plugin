package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.IOException;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.p4.scm.AbstractP4ScmSource.ScmSourceClient;

public class P4Probe extends SCMProbe {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4Probe.class.getName());

	private final String base;

	private transient ClientHelper p4;

	public P4Probe(String credential, TaskListener listener, String charset, String base) {
		this.base = base;
		this.p4 = new ClientHelper(credential, listener, ScmSourceClient, charset);
	}

	@Override
	public String name() {
		return base;
	}

	@Override
	public long lastModified() {
		long last = 0L;
		try {
			last = p4.getHead(base + "/...");
		} catch (Exception e) {
			logger.warning("Unable to check changes: " + e.getMessage());
		}
		return last;
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
			throw new IOException("Unable to check file: " + e.getMessage());
		}
		return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
	}

	@Override
	public void close() throws IOException {
		p4.disconnect();
	}
}