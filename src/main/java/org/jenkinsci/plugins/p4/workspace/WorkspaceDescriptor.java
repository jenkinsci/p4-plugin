package org.jenkinsci.plugins.p4.workspace;

import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.core.IStream;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.option.server.GetClientsOptions;
import com.perforce.p4java.option.server.GetStreamsOptions;
import com.perforce.p4java.server.IOptionsServer;

public abstract class WorkspaceDescriptor extends Descriptor<Workspace> {

	private static Logger logger = Logger.getLogger(WorkspaceDescriptor.class
			.getName());

	public WorkspaceDescriptor(Class<? extends Workspace> clazz) {
		super(clazz);
	}

	protected WorkspaceDescriptor() {
	}

	static public FormValidation checkClientName(String value) {
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
	
	static public AutoCompletionCandidates autoCompleteName(
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
	
	static public ListBoxModel doFillCharsetItems() {
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

	static public AutoCompletionCandidates doAutoCompleteStreamName(
			@QueryParameter String value) {

		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 1) {
				List<String> streamPaths = new ArrayList<String>();
				streamPaths.add(value + "...");
				GetStreamsOptions opts = new GetStreamsOptions();
				opts.setMaxResults(10);
				List<IStreamSummary> list = iserver.getStreams(streamPaths,
						opts);
				for (IStreamSummary l : list) {
					c.add(l.getStream());
				}
			}
		} catch (Exception e) {
		}

		return c;
	}

	/**
	 * Provides auto-completion for workspace names. Stapler finds this
	 * method via the naming convention.
	 * 
	 * @param value
	 *            The text that the user entered.
	 */
	static public AutoCompletionCandidates doAutoCompleteTemplateName(
			@QueryParameter String value) {

		AutoCompletionCandidates c = new AutoCompletionCandidates();
		try {
			IOptionsServer iserver = ConnectionFactory.getConnection();
			if (iserver != null && value.length() > 0) {
				List<IClientSummary> list;
				GetClientsOptions opts = new GetClientsOptions();
				opts.setMaxResults(10);
				opts.setNameFilter(value + "*");
				list = iserver.getClients(opts);
				for (IClientSummary l : list) {
					c.add(l.getName());
				}
			}
		} catch (Exception e) {
		}

		return c;
	}
	
	static public FormValidation doCheckStreamName(@QueryParameter final String value) {
		try {
			IOptionsServer p4 = ConnectionFactory.getConnection();
			IStream stream = p4.getStream(value);
			if (stream != null) {
				return FormValidation.ok();
			}
			return FormValidation.warning("Unknown Stream: " + value);
		} catch (Exception e) {
			return FormValidation.error(e.getMessage());
		}
	}

	static public FormValidation doCheckFormat(@QueryParameter final String value) {
		if (value == null || value.isEmpty())
			return FormValidation.error("Workspace Name format is mandatory.");

		if (value.contains("${") && value.contains("}")) {
			return FormValidation.ok();
		}
		return FormValidation.error("Workspace Name format error.");
	}
}
