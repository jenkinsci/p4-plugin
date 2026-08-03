package org.jenkinsci.plugins.p4.filters;

import com.perforce.p4java.core.IUserSummary;
import com.perforce.p4java.server.IOptionsServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FilterUserImpl extends Filter implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String user;

	@DataBoundConstructor
	public FilterUserImpl(String user) {
		this.user = user;
	}

	public String getUser() {
		return user;
	}

	@Extension
	@Symbol("userFilter")
	public static final class DescriptorImpl extends FilterDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Exclude changes from user";
		}

		public AutoCompletionCandidates doAutoCompleteUser(
				@QueryParameter String value) {

			AutoCompletionCandidates c = new AutoCompletionCandidates();
			try {
				IOptionsServer iserver = ConnectionFactory.getConnection();
				if (iserver != null && !value.isEmpty()) {
					List<String> users = new ArrayList<>();
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
