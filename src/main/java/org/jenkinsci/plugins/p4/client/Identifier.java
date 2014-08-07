package org.jenkinsci.plugins.p4.client;

import java.util.ResourceBundle;

public class Identifier {
	private String product;
	private String version;

	public Identifier() {
		ResourceBundle bundle = ResourceBundle
				.getBundle("org.jenkinsci.plugins.p4.Identifier");

		version = bundle.getString("org.jenkinsci.plugins.p4.version");
		product = bundle.getString("org.jenkinsci.plugins.p4.product");
	}

	public String getVersion() {
		return version;
	}

	public String getProduct() {
		return product;
	}
}
