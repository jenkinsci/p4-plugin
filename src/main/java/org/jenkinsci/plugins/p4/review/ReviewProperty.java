package org.jenkinsci.plugins.p4.review;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;

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
