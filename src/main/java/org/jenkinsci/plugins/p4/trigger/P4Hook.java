package org.jenkinsci.plugins.p4.trigger;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.model.Job;
import hudson.triggers.Trigger;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public class P4Hook implements UnprotectedRootAction {

	// https://github.com/jenkinsci/bitbucket-plugin

	@Override
	public String getIconFileName() {
		return "/plugin/p4/icons/p4.png";
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

			String port = payload.getString("p4port");
			String change = payload.getString("change");

			LOGGER.info("Received trigger event: " + body);
			probeJobs(port, change);
		}
	}
	
	public void doChangeSubmit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {

		JSONObject formData = req.getSubmittedForm();
		if (!formData.isEmpty()) {
			String change = req.getParameter("_.change");
			String port = req.getParameter("_.p4port");
			
			LOGGER.info("Manual trigger event: ");
			probeJobs(port, change);
			
			// send the user back.
			rsp.sendRedirect("../");
		}
	}

	private void probeJobs(String port, String change) throws IOException {
		for (Job<?, ?> job : Jenkins.getInstance().getAllItems(Job.class)) {
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
