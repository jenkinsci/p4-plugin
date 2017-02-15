package org.jenkinsci.plugins.p4.scm;

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
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.browsers.P4Browser;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.credentials.P4CredentialsImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.plugins.p4.workspace.WorkspaceDescriptor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractP4ScmSource extends SCMSource {

	private static Logger logger = Logger.getLogger(AbstractP4ScmSource.class.getName());
	public static String ScmSourceClient = "jenkins-master";

	protected final String credential;

	private final String includes;
	private final String charset;
	private final String format;
	private Populate populate;
	private final P4Browser browser;

	public AbstractP4ScmSource(String id, String credential, String includes, String charset, String format, P4Browser browser) {
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

	public abstract List<P4Head> getHeads(@NonNull TaskListener listener) throws Exception;

	public abstract Workspace getWorkspace(String path);

	@Override
	public SCM build(SCMHead head, SCMRevision revision) {

		if (head instanceof P4Head) {
			P4Head perforceHead = (P4Head) head;
			String path = perforceHead.getPath();

			Workspace workspace = getWorkspace(path);
			PerforceScm scm = new PerforceScm(credential, workspace, null, populate, browser);
			return scm;
		}
		throw new IllegalArgumentException("SCMHead not an instance of PerforceHead!");
	}

	@Override
	protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {
		try {
			List<P4Head> heads = getHeads(listener);
			for (P4Head head : heads) {
				// null criteria means that all branches match.
				if (criteria == null) {
					// get revision and add observe
					SCMRevision revision = getRevision(head, listener);
					observer.observe(head, revision);
				} else {
					String base = head.getPath();
					SCMSourceCriteria.Probe probe = new P4Probe(credential, listener, charset, base);
					if (criteria.isHead(probe, listener)) {
						// get revision and add observe
						SCMRevision revision = getRevision(head, listener);
						observer.observe(head, revision);
					}
				}

				if (Thread.interrupted()) {
					throw new InterruptedException("User abort.");
				}
			}
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	protected List<String> getIncludePaths() {
		String[] array = includes.split("[\\r\\n]+");
		return Arrays.asList(array);
	}

	protected P4Revision getRevision(P4Head head, TaskListener listener) throws Exception {
		try (ClientHelper p4 = new ClientHelper(credential, listener, ScmSourceClient, charset)) {
			long change = p4.getHead(head.getPath() + "/...");
			P4Revision revision = new P4Revision(head, change);
			return revision;
		}
	}

	@Extension
	public static final class DescriptorImpl extends SCMSourceDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

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
}
