package org.jenkinsci.plugins.p4.browsers;

import com.perforce.p4java.core.file.FileAction;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenGrokBrowserTest {

	private static final String SERVER_URL = "http://opengrok.example.com/";
	private static final String DEPOT_PATH = "//depot/core/main";
	private static final String PROJECT_NAME = "core";

	@Test
	void testConstructorGetters() {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);

		assertEquals(DEPOT_PATH, browser.getDepotPath());
		assertEquals(PROJECT_NAME, browser.getProjectName());
	}

	@Test
	void testDiffLinkIsNullWhenRevisionIsOne() throws Exception {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);
		P4AffectedFile file = new P4AffectedFile(DEPOT_PATH + "/src/File.java", "#1", FileAction.EDIT);

		assertNull(browser.getDiffLink(file, null));
	}

	@Test
	void testDiffLinkForRevisionAboveOne() throws Exception {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);
		P4AffectedFile file = new P4AffectedFile(DEPOT_PATH + "/src/File.java", "#3", FileAction.EDIT);

		URL link = browser.getDiffLink(file, null);

		assertEquals("http://opengrok.example.com/source/diff/core/build.properties"
				+ "?r2=src%2FFile.java%233&r1=src%2FFile.java%232src/File.java", link.toString());
	}

	@Test
	void testFileLinkUnderProjectXref() throws Exception {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);
		P4AffectedFile file = new P4AffectedFile(DEPOT_PATH + "/src/File.java", "#3", FileAction.EDIT);

		URL link = browser.getFileLink(file);

		assertEquals("http://opengrok.example.com/source/xref/core/src/File.java", link.toString());
	}

	@Test
	void testJobLinkIsNotImplemented() {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);
		assertNull(browser.getJobLink("job-1"));
	}

	@Test
	void testChangeSetLinkIsStubbedOut() {
		OpenGrokBrowser browser = new OpenGrokBrowser(SERVER_URL, DEPOT_PATH, PROJECT_NAME);
		assertNull(browser.getChangeSetLink(null));
	}

	@Test
	void testDescriptorDisplayName() {
		OpenGrokBrowser.DescriptorImpl descriptor = new OpenGrokBrowser.DescriptorImpl();
		assertEquals("OpenGrok", descriptor.getDisplayName());
	}

	@Test
	void testDescriptorDoCheckAcceptsEmptyValue() {
		OpenGrokBrowser.DescriptorImpl descriptor = new OpenGrokBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("").kind);
	}

	@Test
	void testDescriptorDoCheckAcceptsHttpAndHttpsUrls() {
		OpenGrokBrowser.DescriptorImpl descriptor = new OpenGrokBrowser.DescriptorImpl();

		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("http://opengrok.example.com/").kind);
		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("https://opengrok.example.com/").kind);
	}

	@Test
	void testDescriptorDoCheckRejectsNonHttpUrl() {
		OpenGrokBrowser.DescriptorImpl descriptor = new OpenGrokBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.ERROR, descriptor.doCheck("ftp://opengrok.example.com/").kind);
	}

	@Test
	void testDescriptorNewInstanceReturnsNullWithoutRequest() {
		OpenGrokBrowser.DescriptorImpl descriptor = new OpenGrokBrowser.DescriptorImpl();
		assertNull(descriptor.newInstance((StaplerRequest2) null, new JSONObject()));
	}
}
