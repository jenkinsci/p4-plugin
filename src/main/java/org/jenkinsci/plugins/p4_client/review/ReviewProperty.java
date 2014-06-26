package org.jenkinsci.plugins.p4_client.review;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;
import hudson.model.AbstractProject;

import java.util.Collection;
import java.util.Collections;

@Extension
public class ReviewProperty extends TransientProjectActionFactory {

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Collection<? extends Action> createFor(final AbstractProject project) {
		String scm = project.getScm().getType();
		if (scm != null && scm.contains("PerforceScm")) {
			return Collections.singleton(new ReviewAction(project));
		}
		return Collections.EMPTY_LIST;
	}
}
