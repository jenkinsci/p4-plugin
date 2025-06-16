package org.jenkinsci.plugins.p4.review;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Restricted(NoExternalUse.class)
public class SafeParametersAction extends ParametersAction {

	private List<ParameterValue> internalParameters;

	public SafeParametersAction(@NonNull List<ParameterValue> params, @NonNull List<ParameterValue> internalParameters) {
		// Apply security to regular parameters
		super(params);
		this.internalParameters = internalParameters;
	}

	@Override
	public List<ParameterValue> getParameters() {
		List<ParameterValue> params = new ArrayList<>();
		List<ParameterValue> p = super.getParameters();
		params.addAll(p);
		params.addAll(internalParameters);
		return Collections.unmodifiableList(params);
	}

	@Override
	public ParameterValue getParameter(String name) {
		ParameterValue param = super.getParameter(name);
		if (param != null) {
			return param;
		}
		for (ParameterValue p : internalParameters) {
			if (p == null) continue;
			if (p.getName().equals(name))
				return p;
		}
		return null;
	}

	List<ParameterValue> getInternalParameters() {
		return Collections.unmodifiableList(internalParameters);
	}

	@Extension
	public static final class SafeParametersActionEnvironmentContributor extends EnvironmentContributor {

		@Override
		public void buildEnvironmentFor(@NonNull Run r, @NonNull EnvVars envs, @NonNull TaskListener listener) {
			SafeParametersAction action = r.getAction(SafeParametersAction.class);
			if (action != null) {
				for (ParameterValue pv : action.getInternalParameters()) {
					envs.put(pv.getName(), String.valueOf(pv.getValue()));
				}
			}
		}
	}

}
