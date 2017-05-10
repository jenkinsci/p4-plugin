package org.jenkinsci.plugins.p4.review;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@Extension
public class ReviewActionFactory extends TransientActionFactory<Job> {

	@Override
	public Class<Job> type() {
		return Job.class;
	}

	@Override
	public Collection<? extends Action> createFor(@Nonnull Job target) {
		try {
			if ("WorkflowJob".equals(target.getClass().getName())) {
				return Collections.singletonList(new ReviewAction(target));
			}
		} catch (NoClassDefFoundError e) {
			// Not loaded so just return empty
		}
		return Collections.emptyList();
	}
}