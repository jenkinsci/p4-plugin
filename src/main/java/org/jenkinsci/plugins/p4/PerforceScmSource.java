package org.jenkinsci.plugins.p4;

import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.core.file.IFileSpec;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StreamWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

public class PerforceScmSource extends SCMSource {

	private static Logger logger = Logger.getLogger(PerforceScmSource.class.getName());
	private static String hack = "jenkins-master";

	private final String credential;
	private final String includes;
	private final String charset;
	private final String format;
	private Populate populate;
	private final P4Browser browser;

	@DataBoundConstructor
	public PerforceScmSource(String id, String credential, String includes, String charset, String format, P4Browser browser) {
		super(id);

		this.credential = credential;
		this.includes = includes;
		this.charset = charset;
		this.format = format;
		this.browser = browser;
	}

	@DataBoundSetter
	public void setPopulate(Populate populate) {
		this.populate = populate;
	}

	public String getCredential() {
		return credential;
	}

	public String getIncludes() {
		return includes;
	}

	public String getCharset() {
		return charset;
	}

	public String getFormat() {
		return format;
	}

	public P4Browser getBrowser() {
		return browser;
	}

	public Populate getPopulate() {
		return populate;
	}

	@Override
	protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {

		// check criteria; if null exit early
		if (criteria == null) {
			return;
		}

		try {
			List<PerforceHead> streams = getStreams(listener);
			for (PerforceHead stream : streams) {
				String base = stream.getPath();
				SCMSourceCriteria.Probe probe = new PerforceProbe(listener, base);
				if (criteria.isHead(probe, listener)) {
					// get revision and add observe
					SCMRevision revision = getRevision(stream, listener);
					observer.observe(stream, revision);
				}
			}
		} catch (Exception e) {
			String err = "P4: Unable add streams to observer.";
			logger.severe(err);
			listener.error(err);
			throw new InterruptedException(e.getMessage());
		}
	}

	@Override
	public SCM build(SCMHead head, SCMRevision revision) {

		if (head instanceof PerforceHead) {
			PerforceHead perforceHead = (PerforceHead) head;
			String stream = perforceHead.getPath();

			Workspace workspace = new StreamWorkspaceImpl(charset, false, stream, format);
			PerforceScm scm = new PerforceScm(credential, workspace, null, populate, browser);
			return scm;
		}
		throw new IllegalArgumentException("SCMHead not an instance of PerforceHead!");
	}

	private List<PerforceHead> getStreams(TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<PerforceHead> list = new HashSet<PerforceHead>();

		ConnectionHelper p4 = new ConnectionHelper(credential, listener);
		try {
			List<IStreamSummary> specs = p4.getStreams(paths);
			for (IStreamSummary s : specs) {
				String name = s.getName();
				String stream = s.getStream();
				PerforceHead head = new PerforceHead(name, stream);
				list.add(head);
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<PerforceHead>(list);
	}

	// TODO - use this to support branches for multi-branch
	private List<PerforceHead> getBranches(TaskListener listener) throws Exception {

		List<String> paths = getIncludePaths();
		HashSet<PerforceHead> list = new HashSet<PerforceHead>();

		ConnectionHelper p4 = new ConnectionHelper(credential, listener);
		try {
			for (String path : paths) {
				List<IFileSpec> specs = p4.getDirs(path);
				for (IFileSpec s : specs) {
					String branch = s.getDepotPathString();
					Path depotPath = Paths.get(branch);
					String name = depotPath.getFileName().toString();

					PerforceHead head = new PerforceHead(name, branch);
					list.add(head);
				}
			}
		} finally {
			p4.disconnect();
		}
		return new ArrayList<PerforceHead>(list);
	}

	private List<String> getIncludePaths() {
		String[] array = includes.split("[\\r\\n]+");
		return Arrays.asList(array);
	}

	private PerforceRevision getRevision(PerforceHead head, TaskListener listener) throws Exception {

		ClientHelper p4 = new ClientHelper(credential, listener, hack, charset);
		try {
			long change = p4.getHead(head.getPath() + "/...");

			PerforceRevision revision = new PerforceRevision(head, change);
			return revision;
		} finally {
			p4.disconnect();
		}
	}

	private final class PerforceProbe extends SCMProbe {

		private final String base;
		private final ClientHelper p4;

		public PerforceProbe(final TaskListener listener, final String base) {
			this.base = base;
			p4 = new ClientHelper(credential, listener, hack, charset);
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

	@Extension
	public static final class DescriptorImpl extends SCMSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "Perforce";
		}

		public ListBoxModel doFillCredentialItems() {
			return P4CredentialsImpl.doFillCredentialItems();
		}

		public FormValidation doCheckCredential(@QueryParameter String value) {
			return P4CredentialsImpl.doCheckCredential(value);
		}

		public ListBoxModel doFillCharsetItems() {
			return WorkspaceDescriptor.doFillCharsetItems();
		}

		public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
			return RepositoryBrowsers.filter(P4Browser.class);
		}
	}

	private static final class PerforceHead extends SCMHead {

		private final String path;

		PerforceHead(String name, String path) {
			super(name);
			this.path = path;
		}

		public String getPath() {
			return path;
		}
	}

	private static final class PerforceRevision extends SCMRevision {
		private final long change;

		PerforceRevision(PerforceHead branch, long change) {
			super(branch);
			this.change = change;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			PerforceRevision that = (PerforceRevision) o;
			return change == that.change && getHead().equals(that.getHead());
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return (int) (change ^ (change >>> 32));
		}

		@Override
		public String toString() {
			return Long.toString(change);
		}
	}
}
