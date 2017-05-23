package org.jenkinsci.plugins.p4.trigger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.UnprotectedRootAction;
import hudson.triggers.Trigger;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@Extension
public class P4Hook implements UnprotectedRootAction {

	// https://github.com/jenkinsci/bitbucket-plugin

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	@Override
	public String getIconFileName() {
		return "/plugin/p4/icons/helix-24px.png";
	}

	@Override
	public String getDisplayName() {
		return "P4 Trigger";
	}

	@Override
	public String getUrlName() {
		return "p4";
	}

	public void doChange(StaplerRequest req) throws IOException {
		String body = IOUtils.toString(req.getInputStream());
		String contentType = req.getContentType();
		if (contentType != null && contentType.startsWith("application/json")) {
			body = URLDecoder.decode(body, "UTF-8");
		}
		if (body.startsWith("payload=")) {
			body = body.substring(8);
			JSONObject payload = JSONObject.fromObject(body);

			final String port = payload.getString("p4port");
			final String change = payload.getString("change");

			LOGGER.info("Received trigger event: " + body);
			if (port == null) {
				LOGGER.fine("p4port must be specified");
				return;
			}

			// Use an executor to prevent blocking the trigger during polling
			executorService.submit(new Runnable() {

				@Override
				public void run() {
					try {
						probeJobs(port, change);
					} catch (IOException e) {
						LOGGER.severe("Error on Polling Thread.");
						e.printStackTrace();
					}
				}
			});
		}
	}

	public void doChangeSubmit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {

		JSONObject formData = req.getSubmittedForm();
		if (!formData.isEmpty()) {
			String change = req.getParameter("_.change");
			String port = req.getParameter("_.p4port");

			LOGGER.info("Manual trigger event: ");
			if (port != null) {
				probeJobs(port, change);
			} else {
				LOGGER.fine("p4port must be specified");
			}

			// send the user back.
			rsp.sendRedirect("../");
		}
	}

	private void probeJobs(@CheckForNull String port, String change) throws IOException {
		Jenkins j = Jenkins.getInstance();
		if (j == null) {
			LOGGER.warning("Jenkins instance is null.");
			return;
		}

		for (Job<?, ?> job : j.getAllItems(Job.class)) {
			P4Trigger trigger = null;
			LOGGER.fine("P4: trying: " + job.getName());

			if (job instanceof ParameterizedJobMixIn.ParameterizedJob) {
				ParameterizedJobMixIn.ParameterizedJob pJob = (ParameterizedJobMixIn.ParameterizedJob) job;
				for (Trigger<?> t : pJob.getTriggers().values()) {
					if (t instanceof P4Trigger) {
						trigger = (P4Trigger) t;
						break;
					}
				}
			}

			if (trigger != null) {
				LOGGER.info("P4: probing: " + job.getName());
				trigger.poke(job, port);
			} else {
				LOGGER.fine("P4: trigger not set: " + job.getName());
			}
		}
	}

	final static Logger LOGGER = Logger.getLogger(P4Hook.class.getName());
}
