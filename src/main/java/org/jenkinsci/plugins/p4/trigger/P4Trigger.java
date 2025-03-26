package org.jenkinsci.plugins.p4.trigger;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.StreamTaskListener;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

public class P4Trigger extends Trigger<Job<?, ?>> {

	@DataBoundConstructor
	public P4Trigger() {
	}

	@Extension
	@Symbol("p4Trigger")
	public static class DescriptorImpl extends TriggerDescriptor {

		@Override
		public boolean isApplicable(Item item) {
			return item instanceof Job;
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce triggered build.";
		}
	}

	public void poke(Job<?, ?> job, String port) throws IOException {
		// exit early if job does not match trigger
		if (!matchServer(job, port)) {
			return;
		}

		LOGGER.info("P4: poking: " + job.getName());

		StreamTaskListener listener = new StreamTaskListener(getLogFile(job), Charset.defaultCharset());
		try {
			PrintStream log = listener.getLogger();

			SCMTriggerItem item = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
			if (item == null) {
				LOGGER.severe("Trigger item not found.");
				return;
			}

			PollingResult pollResult = item.poll(listener);
			if (pollResult != null && pollResult.hasChanges()) {
				log.println("P4: Changes found");
				build(job);
			} else {
				log.println("P4: No changes");
			}
		} catch (Exception e) {
			String msg = "P4: Failed to record P4 trigger: ";
			e.printStackTrace(listener.error(msg));
			LOGGER.severe(msg + e);
		} finally {
			listener.close();
		}
	}

	/**
	 * Schedule build
	 *
	 * @param job Jenkins Job
	 * @throws IOException push up stack
	 */
	private void build(final Job<?, ?> job) throws IOException {

		SCMTriggerCause cause = new SCMTriggerCause(getLogFile(job));

		@SuppressWarnings("rawtypes")
		ParameterizedJobMixIn pJob = new ParameterizedJobMixIn() {
			@Override
			protected Job asJob() {
				return job;
			}
		};

		pJob.scheduleBuild(cause);
	}

	public File getLogFile(Job<?, ?> job) {
		if (job == null) {
			return null;
		}
		return new File(job.getRootDir(), "p4trigger.log");
	}

	private boolean matchServer(Job<?, ?> job, String port) {
		//Get all the trigger for this Job
		SCMTriggerItem item = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);

		if (item != null) {
			//As soon as we find a match, return
			for (SCM scmTrigger : item.getSCMs()) {
				PerforceScm p4scm = PerforceScm.convertToPerforceScm(scmTrigger);
				if (p4scm != null) {
					String id = p4scm.getCredential();
					P4BaseCredentials credential = ConnectionHelper.findCredential(id, job);
					if (credential != null
							&& credential.getFullP4port() != null
							&& credential.getFullP4port().contains(port)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	final static Logger LOGGER = Logger.getLogger(P4Trigger.class.getName());

	/**
	 * Perforce Trigger Log Action
	 */
	public final class P4TriggerAction implements Action {
		public Job<?, ?> getOwner() {
			return job;
		}

		public String getIconFileName() {
			return "clipboard.png";
		}

		public String getDisplayName() {
			return "P4 Trigger Log";
		}

		public String getUrlName() {
			return "P4TriggerLog";
		}

		public String getLog() throws IOException {
			return Util.loadFile(getLogFile(job), Charset.defaultCharset());
		}

		/**
		 * Writes the annotated log to the given output.
		 *
		 * @param out XML output
		 * @throws IOException push up stack
		 */
		@SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "This is called from P4TriggerAction Jelly")
		public void writeLogTo(XMLOutput out) throws IOException {
			new AnnotatedLargeText<>(getLogFile(job), Charset.defaultCharset(), true, this).writeHtmlTo(0,
					out.asWriter());
		}
	}

	@Override
	public Collection<? extends Action> getProjectActions() {
		return Collections.singleton(new P4TriggerAction());
	}
}
