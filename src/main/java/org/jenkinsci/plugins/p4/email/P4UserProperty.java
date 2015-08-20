package org.jenkinsci.plugins.p4.email;

import hudson.Extension;
import hudson.Util;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.User;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

public class P4UserProperty extends UserProperty {

	private final String email;

	@DataBoundConstructor
	public P4UserProperty(String email) {
		this.email = Util.fixEmptyAndTrim(email);
	}

	@Extension
	public static class DescriptorImpl extends UserPropertyDescriptor {

		@Override
		public UserProperty newInstance(User user) {
			return new P4UserProperty(null);
		}

		@Override
		public String getDisplayName() {
			return "Perforce User Property";
		}

		@Override
		public UserProperty
				newInstance(StaplerRequest req, JSONObject formData)
						throws FormException {
			return new P4UserProperty(formData.optString("email"));
		}
	}

	@Exported
	public String getEmail() {
		return email;
	}
}
