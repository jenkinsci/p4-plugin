package org.jenkinsci.plugins.p4.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import org.jenkinsci.plugins.p4.client.ClientHelper;

import java.io.IOException;
import java.util.logging.Logger;

public class P4Probe extends SCMProbe {

	private static Logger logger = Logger.getLogger(P4Probe.class.getName());

	private final String base;
	private final ClientHelper p4;

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

	/**
	 * Closes this stream and releases any system resources associated
	 * with it. If the stream is already closed then invoking this
	 * method has no effect.
	 * <p>
	 * <p> As noted in {@link AutoCloseable#close()}, cases where the
	 * close may fail require careful attention. It is strongly advised
	 * to relinquish the underlying resources and to internally
	 * <em>mark</em> the {@code Closeable} as closed, prior to throwing
	 * the {@code IOException}.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		p4.disconnect();
	}
}