package org.jenkinsci.plugins.p4.review;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class ReviewAction<T extends Job<?, ?> & ParameterizedJob> implements Action {

	private final T project;


	public ReviewAction(T project) {
		this.project = project;
	}

	public T getProject() {
		return project;
	}

	public String getIconFileName() {
		return "/plugin/p4/icons/p4.png";
	}

	public String getDisplayName() {
		return "Build Review";
	}

	public String getUrlName() {
		return "review";
	}

	/**
	 * Jelly Method
	 *
	 * @return List of Parameters
	 */
	public List<StringParameterValue> getAvailableParameters() {
		List<StringParameterValue> stringParameters = new ArrayList<StringParameterValue>();

		for (ParameterDefinition parameterDefinition : getParameterDefinitions()) {
			StringParameterValue stringParameter = new StringParameterValue(parameterDefinition.getName(),
					parameterDefinition.getDescription());
			stringParameters.add(stringParameter);
		}

		return stringParameters;
	}

	public void doBuildSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

		JSONObject formData = req.getSubmittedForm();
		if (!formData.isEmpty()) {
			doBuild(req, rsp);
		}
	}

	public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

		project.checkPermission(AbstractProject.BUILD);

		List<ParameterValue> values = new ArrayList<ParameterValue>();
		List<ParameterDefinition> defs = new ArrayList<ParameterDefinition>();

		Enumeration<?> names = req.getParameterNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			defs.add(new StringParameterDefinition(name, null));
		}

		for (ParameterDefinition d : defs) {
			StringParameterValue value = (StringParameterValue) d.createValue(req);
			if (value != null && value.value != null && !value.value.isEmpty()) {
				values.add(value);
			}
		}

		// Schedule build
		TimeDuration delay = new TimeDuration(project.getQuietPeriod());
		CauseAction cause = new CauseAction(new Cause.UserIdCause());

		List<ParameterValue> internalParams = extractAndRemoveInternalParameters(values);
		ParametersAction params = new SafeParametersAction(values, internalParams);

		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			Queue queue = j.getQueue();
			queue.schedule(project, delay.getTime(), params, cause);

			// send the user back to the job top page.
			rsp.sendRedirect("../");
		}
	}

	/**
	 * It extracts and removes internal parameters from the full list of parameters as they need to be managed
	 * in a special way for security reasons (related to SECURITY-170).
	 *
	 * @param values internal parameters values (internal parameters are defined in {@link #getParameterDefinitions()}
	 * @return internal parameters values
	 */
	private List<ParameterValue> extractAndRemoveInternalParameters(List<ParameterValue> values) {
		List<ParameterValue> internal = new ArrayList<ParameterValue>();
		List<ParameterDefinition> parameterDefinitions = getParameterDefinitions();
		Iterator<ParameterValue> it = values.iterator();
		while (it.hasNext()) {
			ParameterValue next = it.next();
			for (ParameterDefinition pd : parameterDefinitions) {
				if (next.getName().equals(pd.getName())) {
					internal.add(next);
					it.remove();
					break;
				}
			}
		}
		return internal;
	}

	private List<ParameterDefinition> getParameterDefinitions() {
		List<ParameterDefinition> swarm = new ArrayList<ParameterDefinition>();

		// Swarm parameters
		swarm.add(new StringParameterDefinition(ReviewProp.REVIEW.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.CHANGE.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.STATUS.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.PASS.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.FAIL.getProp(), null));

		// Custom parameters
		swarm.add(new StringParameterDefinition(ReviewProp.LABEL.toString(), null));

		return swarm;
	}

	final static Logger LOGGER = Logger.getLogger(ReviewAction.class.getName());
}
