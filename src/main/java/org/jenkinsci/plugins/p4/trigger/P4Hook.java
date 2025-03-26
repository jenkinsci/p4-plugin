package org.jenkinsci.plugins.p4.trigger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.UnprotectedRootAction;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.scm.events.P4BranchSCMHeadEvent;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static hudson.Functions.checkPermission;

@Extension
public class P4Hook implements UnprotectedRootAction {

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	public static final String URLNAME = "p4";

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
		return URLNAME;
	}

	@POST
	public void doEvent(StaplerRequest2 req) throws ServletException, IOException {

		checkPermission(Item.BUILD);

		// exit early if no json
		String contentType = req.getContentType();
		if (contentType == null || !contentType.startsWith("application/json")) {
			return;
		}

		String body = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
		if (body.startsWith("payload=")) {
			body = body.substring(8);
		}
		JSONObject payload = JSONObject.fromObject(body);

		String typeString = payload.getString(ReviewProp.EVENT_TYPE.getProp());
		SCMEvent.Type eventType = SCMEvent.Type.valueOf(typeString);

		SCMHeadEvent.fireNow(new P4BranchSCMHeadEvent(eventType, payload, SCMEvent.originOf(req)));
	}

	@POST
	public void doChange(StaplerRequest2 req) throws ServletException, IOException {

		checkPermission(Item.BUILD);

		String body = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
		String contentType = req.getContentType();
		if (contentType != null && contentType.startsWith("application/json")) {
			body = URLDecoder.decode(body, StandardCharsets.UTF_8);
		}
		if (body.startsWith("payload=")) {
			body = body.substring(8);
			JSONObject payload = JSONObject.fromObject(body);

			final String port = payload.getString("p4port");
			//final String change = payload.getString("change");
			final List<Job> jobs = getJobs();

			LOGGER.info("Received trigger event for: " + port);
			if (port == null) {
				LOGGER.warning("p4port must be specified");
				return;
			}

			// Use an executor to prevent blocking the trigger during polling
			executorService.submit(() -> {
				try {
					probeJobs(port, jobs);
				} catch (IOException e) {
					LOGGER.severe("Error on Polling Thread.");
					e.printStackTrace();
				}
			});
		}
	}

	@POST
	public void doChangeSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {

		checkPermission(Item.BUILD);

		JSONObject formData = req.getSubmittedForm();
		if (!formData.isEmpty()) {
			String port = req.getParameter("_.p4port");
			//String change = req.getParameter("_.change");
			List<Job> jobs = getJobs();

			LOGGER.info("Manual trigger event: ");
			if (port != null) {
				probeJobs(port, jobs);
			} else {
				LOGGER.warning("p4port must be specified");
			}

			// send the user back.
			rsp.sendRedirect("../");
		}
	}

	private void probeJobs(@CheckForNull String port, List<Job> jobs) throws IOException {
		for (Job<?, ?> job : jobs) {
			if (!job.isBuildable()) {
				continue;
			}
			LOGGER.fine("P4: trying: " + job.getName());

			P4Trigger trigger = null;
			if (job instanceof ParameterizedJobMixIn.ParameterizedJob pJob) {
				for (Object t : pJob.getTriggers().values()) {
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

	private List<Job> getJobs() {
		Jenkins j = Jenkins.get();
		return j.getAllItems(Job.class);
	}

	final static Logger LOGGER = Logger.getLogger(P4Hook.class.getName());
}
