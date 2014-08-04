package org.jenkinsci.plugins.p4.client;

import hudson.PluginWrapper;
import jenkins.model.Jenkins;

public class Identifier {
	private String product;
	private String version;

	public Identifier() {
		Jenkins jenkins = Jenkins.getInstance();
		PluginWrapper plugin = jenkins.getPluginManager().getPlugin("p4");
		String string = plugin.getVersion();
		String[] parts = string.split(" ");

		version = parts[0];
		product = "Jenkins_p4-plugin";
	}

	public String getVersion() {
		return version;
	}

	public String getProduct() {
		return product;
	}
}
