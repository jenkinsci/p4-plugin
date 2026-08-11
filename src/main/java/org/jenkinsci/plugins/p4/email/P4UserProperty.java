package org.jenkinsci.plugins.p4.email;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;

public class P4UserProperty extends UserProperty {

	private final String email;

	@DataBoundConstructor
	public P4UserProperty(String email) {
		this.email = Util.fixEmptyAndTrim(email);
	}

	@Extension
	@Symbol("user")
	public static class DescriptorImpl extends UserPropertyDescriptor {

		@Override
		public UserProperty newInstance(User user) {
			return new P4UserProperty(null);
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Perforce User Property";
		}

		@Override
		public UserProperty
		newInstance(StaplerRequest2 req, @NonNull JSONObject formData) {
			return new P4UserProperty(formData.optString("email"));
		}
	}

	@Exported
	public String getEmail() {
		return email;
	}
}
