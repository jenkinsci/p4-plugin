package org.jenkinsci.plugins.p4.email;

import hudson.model.User;
import hudson.tasks.MailAddressResolver;

import java.util.logging.Logger;

public class P4AddressResolver extends MailAddressResolver {

	private static Logger logger = Logger.getLogger(P4AddressResolver.class
			.getName());

	@Override
	public String findMailAddressFor(User user) {
		P4UserProperty prop = user.getProperty(P4UserProperty.class);
		if (prop != null) {
			String id = user.getId();
			String email = prop.getEmail();
			logger.info("MailAddressResolver: " + id + ":" + email);
			return email;
		}
		return null;
	}
}
