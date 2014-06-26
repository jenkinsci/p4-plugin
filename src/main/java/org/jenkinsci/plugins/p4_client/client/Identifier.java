package org.jenkinsci.plugins.p4_client.client;

public class Identifier {
	private String product;
	private String version;
	
	public Identifier() {
		Package p = this.getClass().getPackage();
		version = p.getSpecificationVersion();
		if(version == null)
			version = "UNSET";
		product = p.getImplementationTitle();
		if(product == null)
			product = "Jenkins p4-plugin";
	}
	
	public String getVersion() {
		return version;
	}
	
	public String getProduct() {
		return product;
	}
}
