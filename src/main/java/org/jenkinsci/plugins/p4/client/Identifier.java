package org.jenkinsci.plugins.p4.client;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Identifier {
	private String product;
	private String version;

	public Identifier() throws IOException, XmlPullParserException {
		MavenXpp3Reader reader = new MavenXpp3Reader();
		Model model = reader.read(new InputStreamReader(new FileInputStream("pom.xml"), "UTF-8"));

		version = model.getVersion();
		product = model.getArtifactId();

		String platform = System.getProperty("os.name");
		platform = platform.replaceAll(" ", "_");
		version += "/" + platform;
	}

	public String getVersion() {
		return version;
	}

	public String getProduct() {
		return product;
	}
}
