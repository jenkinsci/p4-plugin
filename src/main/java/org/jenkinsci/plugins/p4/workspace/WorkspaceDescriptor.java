package org.jenkinsci.plugins.p4.workspace;

import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.server.IOptionsServer;

public abstract class WorkspaceDescriptor extends Descriptor<Workspace> {

	private static Logger logger = Logger.getLogger(WorkspaceDescriptor.class
			.getName());

	public WorkspaceDescriptor(Class<? extends Workspace> clazz) {
		super(clazz);
	}

	protected WorkspaceDescriptor() {
	}

	/**
	 * Magic Stapler method; provides list of Charset types.
	 */
	public ListBoxModel doFillCharsetItems() {
		ListBoxModel list = new ListBoxModel();
		try {
			IOptionsServer p4 = ConnectionFactory.getConnection();
			for (String set : p4.getKnownCharsets()) {
				list.add(set);
			}
		} catch (Exception e) {
		}
		if (list.isEmpty()) {
			list.add("none");
		}
		return list;
	}

	public FormValidation checkClientName(String value) {
		try {
			IOptionsServer p4 = ConnectionFactory.getConnection();
			if (p4 == null) {
				// refresh issue; sometimes not available
				return FormValidation.ok();
			}
			IClient client = p4.getClient(value);
			if (client != null) {
				return FormValidation.ok();
			}
			return FormValidation.warning("Unknown Client: " + value);
		} catch (Exception e) {
			return FormValidation.error(e.getMessage());
		}
	}
	
	public AutoCompletionCandidates autoCompleteName(
			@QueryParameter String value) {

		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 0) {
				String user = iserver.getUserName();
				List<IClientSummary> list;
				list = iserver.getClients(user, value + "*", 10);
				for (IClientSummary l : list) {
					c.add(l.getName());
				}
			}
		} catch (Exception e) {
		}

		return c;
	}
}
