package org.jenkinsci.plugins.p4.populate;

import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;

import java.util.List;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.core.ILabelSummary;
import com.perforce.p4java.option.server.GetLabelsOptions;
import com.perforce.p4java.server.IOptionsServer;

public abstract class PopulateDescriptor extends Descriptor<Populate> {

	public PopulateDescriptor(Class<? extends Populate> clazz) {
		super(clazz);
	}

	protected PopulateDescriptor() {
	}

	public AutoCompletionCandidates doAutoCompletePin(
			@QueryParameter String value) {
		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 0) {
				List<ILabelSummary> list;
				GetLabelsOptions opts = new GetLabelsOptions();
				opts.setMaxResults(10);
				opts.setNameFilter(value + "*");
				list = iserver.getLabels(null, opts);
				for (ILabelSummary l : list) {
					c.add(l.getName());
				}
			}
		} catch (Exception e) {
		}

		return c;
	}
}
