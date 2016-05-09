package org.jenkinsci.plugins.p4.trigger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.util.StreamTaskListener;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;

public class P4Trigger extends Trigger<Job<?, ?>> {

	@DataBoundConstructor
	public P4Trigger() {
	}

	@Extension
	public static class DescriptorImpl extends TriggerDescriptor {

		@Override
		public boolean isApplicable(Item item) {
			return item instanceof Job;
		}

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

		StreamTaskListener listener = new StreamTaskListener(getLogFile());
		try {
			PrintStream log = listener.getLogger();

			SCMTriggerItem item = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);

			PollingResult pollResult = item.poll(listener);

			if (pollResult.hasChanges()) {
				log.println("Changes found");
				build(job);
			} else {
				log.println("No changes");
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
	 * @param job
	 * @throws IOException
	 */
	private void build(final Job<?, ?> job) throws IOException {

		SCMTriggerCause cause = new SCMTriggerCause(getLogFile());

		@SuppressWarnings("rawtypes")
		ParameterizedJobMixIn pJob = new ParameterizedJobMixIn() {
			@Override
			protected Job asJob() {
				return job;
			}
		};

		pJob.scheduleBuild(cause);
	}

	public File getLogFile() {
		return new File(job.getRootDir(), "p4trigger.log");
	}

	private boolean matchServer(Job<?, ?> job, String port) {
		if (job instanceof AbstractProject) {
			AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;
			SCM scm = project.getScm();

			if (scm instanceof PerforceScm) {
				PerforceScm p4scm = (PerforceScm) scm;
				String id = p4scm.getCredential();
				P4BaseCredentials credential = ConnectionHelper.findCredential(id);
				if (port.equals(credential.getP4port())) {
					return true;
				}
			}
		}
		return false;
	}

	final static Logger LOGGER = Logger.getLogger(P4Trigger.class.getName());

	/**
	 * Perforce Trigger Log Action
	 * 
	 * @author pallen
	 *
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
			return Util.loadFile(getLogFile());
		}

		/**
		 * Writes the annotated log to the given output.
		 */
		public void writeLogTo(XMLOutput out) throws IOException {
			new AnnotatedLargeText<P4TriggerAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0,
					out.asWriter());
		}
	}

	@Override
	public Collection<? extends Action> getProjectActions() {
		return Collections.singleton(new P4TriggerAction());
	}
}
