package org.jenkinsci.plugins.p4.review;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@Extension
public class ReviewActionFactory extends TransientActionFactory<Job> {

	@Override
	public Class<Job> type() {
		return Job.class;
	}

	@NonNull
	@Override
	public Collection<? extends Action> createFor(@Nonnull Job target) {
		try {
			if (target instanceof WorkflowJob) {
				return Collections.singletonList(new ReviewAction(target));
			}
		} catch (NoClassDefFoundError e) {
			// Not loaded so just return empty
		}
		return Collections.emptyList();
	}
}