package org.jenkinsci.plugins.p4_client.filters;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.p4_client.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.core.IUserSummary;
import com.perforce.p4java.server.IOptionsServer;

public class FilterUserImpl extends Filter {

	private final String user;

	@DataBoundConstructor
	public FilterUserImpl(String user) {
		this.user = user;
	}

	public String getUser() {
		return user;
	}

	@Extension
	public static final class DescriptorImpl extends FilterDescriptor {

		@Override
		public String getDisplayName() {
			return "Exclude changes from user";
		}

		public AutoCompletionCandidates doAutoCompleteUser(
				@QueryParameter String value) {

			AutoCompletionCandidates c = new AutoCompletionCandidates();
			try {
				IOptionsServer iserver = ConnectionFactory.getConnection();
				if (iserver != null && value.length() > 0) {
					List<String> users = new ArrayList<String>();
					users.add(value + "*");
					List<IUserSummary> list;
					list = iserver.getUsers(users, 10);
					for (IUserSummary l : list) {
						c.add(l.getLoginName());
					}
				}
			} catch (Exception e) {
			}

			return c;
		}
	}
}
