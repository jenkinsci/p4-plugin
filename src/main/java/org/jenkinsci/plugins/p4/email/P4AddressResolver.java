package org.jenkinsci.plugins.p4.email;

import hudson.Extension;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;
import org.jenkinsci.Symbol;

import java.util.logging.Logger;

@Extension
@Symbol("email")
public class P4AddressResolver extends MailAddressResolver {
	@Override
	public String findMailAddressFor(User user) {
		P4UserProperty prop = user.getProperty(P4UserProperty.class);
		if (prop != null) {
			String id = user.getId();
			String email = prop.getEmail();
			return email;
		}
		return null;
	}
}
