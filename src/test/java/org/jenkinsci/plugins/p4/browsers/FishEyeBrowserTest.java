package org.jenkinsci.plugins.p4.browsers;

import com.perforce.p4java.core.file.FileAction;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.p4.changes.P4AffectedFile;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.MockedStatic;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class FishEyeBrowserTest {

	private static final String BROWSE_URL = "http://deadlock.netbeans.org/fisheye/browse/netbeans/";
	private static final String ROOT_MODULE = "netbeans";

	private P4ChangeEntry newChangeEntry(long change) {
		try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
			jenkinsStatic.when(Jenkins::get).thenReturn(null);
			P4ChangeEntry entry = new P4ChangeEntry(null);
			entry.setId(new P4ChangeRef(change));
			return entry;
		}
	}

	@Test
	void testRootModuleIsTrimmedOfLeadingSlashes() {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, "/" + ROOT_MODULE);
		assertEquals(ROOT_MODULE, browser.getRootModule());
	}

	@Test
	void testChangeSetLinkPointsAtChangelogWithChangeNumber() throws Exception {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		P4ChangeEntry changeSet = newChangeEntry(1234);

		URL link = browser.getChangeSetLink(changeSet);

		assertEquals("http://deadlock.netbeans.org/fisheye/changelog/netbeans/?cs=1234", link.toString());
	}

	@Test
	void testDiffLinkIsNullForNonEditAction() throws Exception {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		P4AffectedFile file = new P4AffectedFile("//netbeans/src/File.java", "#3", FileAction.ADD);

		assertNull(browser.getDiffLink(file, new P4ChangeRef(1234)));
	}

	@Test
	void testDiffLinkIsNullWhenChangeIsNull() throws Exception {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		P4AffectedFile file = new P4AffectedFile("//netbeans/src/File.java", "#3", FileAction.EDIT);

		assertNull(browser.getDiffLink(file, null));
	}

	@Test
	void testDiffLinkForEditActionIncludesRevisionQueryParams() throws Exception {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		P4AffectedFile file = new P4AffectedFile("//netbeans/src/File.java", "#3", FileAction.EDIT);

		URL link = browser.getDiffLink(file, new P4ChangeRef(1234));

		assertEquals("http://deadlock.netbeans.org/fisheye/browse/netbeans/src/File.java?r1=&r2=1234", link.toString());
	}

	@Test
	void testFileLinkTrimsRootModuleFromPath() throws Exception {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		P4AffectedFile file = new P4AffectedFile("//netbeans/src/File.java", "#3", FileAction.EDIT);

		URL link = browser.getFileLink(file);

		assertEquals("http://deadlock.netbeans.org/fisheye/browse/netbeans/src/File.java", link.toString());
	}

	@Test
	void testJobLinkIsNotImplemented() {
		FishEyeBrowser browser = new FishEyeBrowser(BROWSE_URL, ROOT_MODULE);
		assertNull(browser.getJobLink("job-1"));
	}

	@Test
	void testDescriptorDisplayName() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertEquals("FishEye browser", descriptor.getDisplayName());
	}

	@Test
	void testDescriptorDoCheckAcceptsEmptyValue() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("").kind);
	}

	@Test
	void testDescriptorDoCheckAcceptsValidBrowseUrl() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("http://fisheye/browse/foobar/").kind);
	}

	@Test
	void testDescriptorDoCheckAppendsMissingTrailingSlashBeforeMatching() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.OK, descriptor.doCheck("http://fisheye/browse/foobar").kind);
	}

	@Test
	void testDescriptorDoCheckRejectsUrlWithoutBrowseSegment() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertEquals(FormValidation.Kind.ERROR, descriptor.doCheck("http://fisheye/nomatch/foobar/").kind);
	}

	@Test
	void testDescriptorNewInstanceReturnsNullWithoutRequest() {
		FishEyeBrowser.DescriptorImpl descriptor = new FishEyeBrowser.DescriptorImpl();
		assertNull(descriptor.newInstance((StaplerRequest2) null, new JSONObject()));
	}
}
