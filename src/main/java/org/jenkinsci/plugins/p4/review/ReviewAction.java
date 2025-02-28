package org.jenkinsci.plugins.p4.review;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Item;
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
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class ReviewAction<T extends Job<?, ?> & ParameterizedJob> implements Action {

	private final T project;

	public ReviewAction(T project) {
		this.project = project;
	}

	public T getProject() {
		return project;
	}

	public String getIconFileName() {
		return "/plugin/p4/icons/swarm-24px.png";
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

	@POST
	public void doBuildSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {

		project.checkPermission(Item.BUILD);

		JSONObject formData = req.getSubmittedForm();
		if (!formData.isEmpty()) {
			doBuild(req, rsp);
		}
	}

	@POST
	public void doBuild(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {

		project.checkPermission(Item.BUILD);

		List<ParameterValue> values = new ArrayList<ParameterValue>();
		List<ParameterDefinition> defs = new ArrayList<ParameterDefinition>();

		Enumeration<?> names = req.getParameterNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			defs.add(new StringParameterDefinition(name, null));
		}

		for (ParameterDefinition d : defs) {
			StringParameterValue value = (StringParameterValue) d.createValue(req);
			if (value == null || value.getValue() == null) {
				continue;
			}
			String s = (String) value.getValue();
			if (s != null && !s.isEmpty()) {
				values.add(value);
			}
		}

		// Schedule build
		TimeDuration delay = new TimeDuration(project.getQuietPeriod());
		CauseAction cause = new CauseAction(new Cause.UserIdCause());

		List<ParameterValue> internalParams = extractAndRemoveInternalParameters(values);
		ParametersAction params = new SafeParametersAction(values, internalParams);

		Jenkins j = Jenkins.getInstance();
		Queue queue = j.getQueue();
		queue.schedule(project, delay.getTimeInSeconds(), params, cause);

		// send the user back to the job top page.
		rsp.sendRedirect("../");
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
		swarm.add(new StringParameterDefinition(ReviewProp.SWARM_REVIEW.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.P4_CHANGE.getProp(), null));
		swarm.add(new ChoiceParameterDefinition(ReviewProp.SWARM_STATUS.getProp(),
							new String[]{"shelved", "committed", "submitted"}, "The review status"));
		swarm.add(new StringParameterDefinition(ReviewProp.SWARM_PASS.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.SWARM_FAIL.getProp(), null));
		swarm.add(new StringParameterDefinition(ReviewProp.SWARM_UPDATE.getProp(), null));

		// Custom parameters
		swarm.add(new StringParameterDefinition(ReviewProp.P4_LABEL.toString(), null));

		return swarm;
	}
}
