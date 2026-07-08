package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.option.client.AddFilesOptions;
import com.perforce.p4java.option.client.EditFilesOptions;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.client.ShelveFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.client.UnshelveFilesOptions;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.PollingResult;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.ParallelSync;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive content-integrity tests for files carrying a UTF-8 BOM through P4Java
 * into a Jenkins workspace.
 *
 * <p>Background: JENKINS-76454 reported that a {@code text+D} file whose content begins
 * with a UTF-8 BOM ({@code EF BB BF}) was corrupted during the sync/transfer step. Every
 * test submits a file with <em>raw bytes</em> and a <em>forced filetype</em> (never a
 * String round-trip, which would hide the BOM), moves it through a real Jenkins operation,
 * and reads the bytes back to assert the BOM and body survived. Digest integrity is
 * proven implicitly: a corrupted transfer makes P4Java abort, so a SUCCESS build plus a
 * byte-verbatim assertion is the guard.
 *
 * <p>Proven by red/green: on p4java 2025.2.2917314 the {@code text+D}+BOM, double-BOM,
 * large-file and force-clean cases go red (aborted build or mangled BOM); on
 * 2026.1.2989454 all pass.
 *
 * <p>Coverage (single self-contained file; helpers at the bottom):
 * <ul>
 *     <li>§A filetypes: text, text+D, text+k, text+C, text+F, text+x, text+w, binary</li>
 *     <li>§B BOM variants: mid-file, double, utf16 leading bytes, BOM-only, BOM+newline, empty</li>
 *     <li>§I controls: plain ASCII, arbitrary binary</li>
 *     <li>§C line-endings: UNIX / WIN / MAC / SHARE</li>
 *     <li>§D size: multi-MB ASCII and multi-MB multibyte-UTF-8 bodies</li>
 *     <li>§E populate: AutoClean, ForceClean+mixed, SyncOnly, revision pinning, parallel</li>
 *     <li>§F idempotency: rebuild stable, polling no-change, force-clean x2</li>
 *     <li>§G unicode twin-risk: single BOM, add BOM, utf16, cross-charset no-double, binary bypass</li>
 *     <li>§H adjacent paths: shelve/unshelve, print, reconcile-no-edit</li>
 * </ul>
 */
@WithJenkins
@Issue("JENKINS-76454")
class BomTest extends DefaultEnvironment {

	private static final Logger LOGGER = Logger.getLogger(BomTest.class.getName());
	private static final String P4ROOT = "tmp-BomTest-p4root";

	/** UTF-8 byte order mark. */
	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	// ==================================================================
	// §A - filetypes
	// ==================================================================

	@Test
	void testTextDeltaWithBom() throws Exception {
		// The reported repro: text+D file whose content leads with a UTF-8 BOM.
		String depotPath = "//depot/bom/text_delta.txt";
		byte[] content = concat(BOM, "line1\nline2\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");
		assertTrue(getHeadType(depotPath).startsWith("text"), "fixture should be a text filetype");

		byte[] synced = syncAndReadBack("text-delta", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced, "BOM + body must transfer verbatim");
	}

	@Test
	void testTextWithBom() throws Exception {
		String depotPath = "//depot/bom/text.txt";
		byte[] content = concat(BOM, "plain text body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("text", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testTextKeywordWithBom() throws Exception {
		// Keyword expansion (+k) must still happen, and the BOM must be preserved.
		String depotPath = "//depot/bom/keyword.txt";
		byte[] content = concat(BOM, "$Id$\nkeyword body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+k");

		byte[] synced = syncAndReadBack("keyword", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);

		String body = new String(synced, StandardCharsets.UTF_8);
		assertTrue(body.contains("$Id:"), "keyword $Id$ should have expanded: " + body);
		assertFalse(body.contains("$Id$"), "unexpanded keyword should not remain: " + body);
	}

	@Test
	void testTextCompressedWithBom() throws Exception {
		// text+C stores the full file compressed - the deflate/inflate path.
		String depotPath = "//depot/bom/compressed.txt";
		byte[] content = concat(BOM, "compressed storage body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+C");

		byte[] synced = syncAndReadBack("compressed", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testTextFullFileWithBom() throws Exception {
		// text+F stores the full file (no RCS delta) - a distinct storage path.
		String depotPath = "//depot/bom/fullfile.txt";
		byte[] content = concat(BOM, "full-file storage body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+F");

		byte[] synced = syncAndReadBack("fullfile", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testTextExecutableWithBom() throws Exception {
		// text+x - executable bit set; content path must be unaffected.
		String depotPath = "//depot/bom/exec.txt";
		byte[] content = concat(BOM, "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+x");
		assertTrue(getHeadType(depotPath).contains("x"), "fixture should carry the +x modifier");

		byte[] synced = syncAndReadBack("exec", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testTextWritableWithBom() throws Exception {
		// text+w - always-writable; content path must be unaffected.
		String depotPath = "//depot/bom/writable.txt";
		byte[] content = concat(BOM, "always writable body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+w");

		byte[] synced = syncAndReadBack("writable", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testBinaryContainingBomBytes() throws Exception {
		// Control: bytes that happen to look like a BOM in a binary file are just data.
		String depotPath = "//depot/bom/binary.bin";
		byte[] content = concat(BOM, new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x7F, 0x00});

		submitFileAsType(depotPath, content, "binary");
		assertEquals("binary", getHeadType(depotPath));

		byte[] synced = syncAndReadBack("binary", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced, "binary content must be byte-for-byte identical");
	}

	// ==================================================================
	// §B - BOM variants / position
	// ==================================================================

	@Test
	void testBomMidFile() throws Exception {
		String depotPath = "//depot/bom/midfile.txt";
		byte[] content = concat("abc".getBytes(StandardCharsets.UTF_8),
				concat(BOM, "def\n".getBytes(StandardCharsets.UTF_8)));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("midfile", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced, "a mid-file BOM sequence must survive verbatim");
	}

	@Test
	void testDoubleBom() throws Exception {
		String depotPath = "//depot/bom/double.txt";
		byte[] content = concat(concat(BOM, BOM), "body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("double", depotPath, new AutoCleanImpl());
		assertStartsWith(concat(BOM, BOM), synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testUtf16LeBomLeadingBytes() throws Exception {
		// FF FE leading bytes in a plain-text file on a non-unicode server are just data.
		String depotPath = "//depot/bom/utf16le.txt";
		byte[] content = concat(new byte[]{(byte) 0xFF, (byte) 0xFE},
				"le body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("utf16le", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced, "FF FE leading bytes must survive verbatim");
	}

	@Test
	void testUtf16BeBomLeadingBytes() throws Exception {
		String depotPath = "//depot/bom/utf16be.txt";
		byte[] content = concat(new byte[]{(byte) 0xFE, (byte) 0xFF},
				"be body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("utf16be", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced, "FE FF leading bytes must survive verbatim");
	}

	@Test
	void testBomOnlyFile() throws Exception {
		String depotPath = "//depot/bom/bomonly.txt";
		byte[] content = BOM.clone();

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("bomonly", depotPath, new AutoCleanImpl());
		assertArrayEquals(BOM, synced, "a BOM-only file must remain exactly 3 bytes");
	}

	@Test
	void testBomThenNewline() throws Exception {
		String depotPath = "//depot/bom/bomnewline.txt";
		byte[] content = concat(BOM, "\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("bomnewline", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertEquals('\n', synced[synced.length - 1], "trailing newline must be preserved");
	}

	@Test
	void testEmptyFile() throws Exception {
		String depotPath = "//depot/bom/empty.txt";
		byte[] content = new byte[0];

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("empty", depotPath, new AutoCleanImpl());
		assertEquals(0, synced.length, "empty file must stay empty");
	}

	// ==================================================================
	// §I - controls (no-BOM baselines)
	// ==================================================================

	@Test
	void testPlainAsciiNoBom() throws Exception {
		String depotPath = "//depot/bom/plain.txt";
		byte[] content = "hello world\nsecond line\n".getBytes(StandardCharsets.UTF_8);

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("plain", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced);
		assertFalse(startsWith(BOM, synced), "control file must not gain a BOM");
	}

	@Test
	void testArbitraryBinary() throws Exception {
		String depotPath = "//depot/bom/arbitrary.bin";
		byte[] content = new byte[256];
		for (int i = 0; i < 256; i++) {
			content[i] = (byte) i;
		}

		submitFileAsType(depotPath, content, "binary");

		byte[] synced = syncAndReadBack("arbitrary", depotPath, new AutoCleanImpl());
		assertArrayEquals(content, synced, "every byte value 0x00..0xFF must round-trip");
	}

	// ==================================================================
	// §C - line-ending translation must not disturb the BOM
	// ==================================================================

	@Test
	void testUnixLineEndPreservesBom() throws Exception {
		byte[] synced = syncLineEnd("unix", "//depot/bom/eol_unix.txt", "UNIX");
		assertStartsWith(BOM, synced);
		assertEquals(0, countOccurrences(synced, new byte[]{'\r'}), "UNIX must have no CR");
		assertEquals(3, countOccurrences(synced, new byte[]{'\n'}), "UNIX keeps three LF");
	}

	@Test
	void testWinLineEndPreservesBom() throws Exception {
		byte[] synced = syncLineEnd("win", "//depot/bom/eol_win.txt", "WIN");
		assertStartsWith(BOM, synced);
		assertEquals(3, countOccurrences(synced, new byte[]{'\r', '\n'}), "WIN translates each newline to CRLF");
	}

	@Test
	void testMacLineEndPreservesBom() throws Exception {
		byte[] synced = syncLineEnd("mac", "//depot/bom/eol_mac.txt", "MAC");
		assertStartsWith(BOM, synced);
		assertEquals(3, countOccurrences(synced, new byte[]{'\r'}), "MAC translates each newline to CR");
		assertEquals(0, countOccurrences(synced, new byte[]{'\n'}), "MAC must have no LF");
	}

	@Test
	void testShareLineEndPreservesBom() throws Exception {
		byte[] synced = syncLineEnd("share", "//depot/bom/eol_share.txt", "SHARE");
		assertStartsWith(BOM, synced);
		assertEquals(0, countOccurrences(synced, new byte[]{'\r'}), "SHARE writes LF to the workspace");
		assertEquals(3, countOccurrences(synced, new byte[]{'\n'}), "SHARE keeps three LF");
	}

	/** Submit BOM + three LF lines, sync with the given client line-ending, return bytes. */
	private byte[] syncLineEnd(String name, String depotPath, String line) throws Exception {
		byte[] content = concat(BOM, "line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text");
		return syncAndReadBack("eol-" + name, depotPath, "none", line, new AutoCleanImpl());
	}

	// ==================================================================
	// §D - size / streaming boundary
	// ==================================================================

	@Test
	void testLargeAsciiFileWithBom() throws Exception {
		// A multi-MB ASCII body behind a leading BOM - the streaming path most likely to
		// drop or shift bytes if a fix is only partial.
		String depotPath = "//depot/bom/large_ascii.txt";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 200_000; i++) {
			sb.append("the quick brown fox jumps over the lazy dog\n");
		}
		byte[] content = concat(BOM, sb.toString().getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("large-ascii", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced, "large BOM'd ASCII file must transfer verbatim");
	}

	@Test
	void testLargeMultibyteFileWithBom() throws Exception {
		// A large multibyte-UTF-8 body behind a BOM - the realistic Unicode case, and a
		// second guard on multi-byte char boundaries straddling the transfer buffer.
		String depotPath = "//depot/bom/large_multibyte.txt";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 150_000; i++) {
			sb.append("café über naïve — 日本語 テスト résumé\n");
		}
		byte[] content = concat(BOM, sb.toString().getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("large-mb", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced, "large BOM'd multibyte file must transfer verbatim");
	}

	// ==================================================================
	// §E - populate / sync operations
	// ==================================================================

	@Test
	void testAutoCleanWithBom() throws Exception {
		String depotPath = "//depot/bompop/autoclean.txt";
		byte[] content = concat(BOM, "auto clean body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");

		byte[] synced = syncAndReadBack("autoclean", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testForceCleanMixedFiletypesWithBom() throws Exception {
		// ForceClean does a full re-transfer + digest recheck (strongest integrity check).
		// Pull text+D+BOM, binary and plain-text together in one sync; none must
		// cross-contaminate (a stray BOM added to or stripped from the wrong file).
		String base = "//depot/bommix";
		byte[] textBom = concat(BOM, "force clean text with bom\n".getBytes(StandardCharsets.UTF_8));
		byte[] binary = concat(BOM, new byte[]{0x00, 0x01, (byte) 0xFF, 0x02});
		byte[] plain = "plain ascii no bom\n".getBytes(StandardCharsets.UTF_8);

		submitFileAsType(base + "/text_bom.txt", textBom, "text+D");
		submitFileAsType(base + "/blob.bin", binary, "binary");
		submitFileAsType(base + "/plain.txt", plain, "text");

		FreeStyleBuild build = buildDirSync("bom-mix", base + "/...", "none", null,
				new ForceCleanImpl(false, false, null, null));

		byte[] syncedTextBom = readWorkspaceBytes(build, "text_bom.txt");
		assertStartsWith(BOM, syncedTextBom);
		assertArrayEquals(textBom, syncedTextBom, "text+D+BOM must transfer verbatim under force-clean");
		assertArrayEquals(binary, readWorkspaceBytes(build, "blob.bin"), "binary must be verbatim");
		assertArrayEquals(plain, readWorkspaceBytes(build, "plain.txt"), "plain must stay BOM-free");
	}

	@Test
	void testSyncOnlyWithBom() throws Exception {
		String depotPath = "//depot/bompop/synconly.txt";
		byte[] content = concat(BOM, "sync only body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");

		Populate syncOnly = new SyncOnlyImpl(false, true, false, false, null, null);
		byte[] synced = syncAndReadBack("synconly", depotPath, "none", null, syncOnly);
		assertStartsWith(BOM, synced);
		assertArrayEquals(content, synced);
	}

	@Test
	void testRevisionPinningWithBom() throws Exception {
		// Two revisions of the same BOM'd file; sync forward to #2 (head) then back to #1
		// by pinning the populate at each change - covers at-changelist and backward sync.
		String depotPath = "//depot/bompop/revisions.txt";
		byte[] rev1 = concat(BOM, "revision one\n".getBytes(StandardCharsets.UTF_8));
		byte[] rev2 = concat(BOM, "revision two\n".getBytes(StandardCharsets.UTF_8));

		long c1 = submitFileAsType(depotPath, rev1, "text+D");
		long c2 = editFileAsType(depotPath, rev2, "text+D");

		byte[] atHead = syncAndReadBack("rev-head", depotPath, "none", null,
				new AutoCleanImpl(true, true, false, false, false, Long.toString(c2), null));
		assertStartsWith(BOM, atHead);
		assertArrayEquals(rev2, atHead, "head revision must carry its BOM verbatim");

		byte[] atFirst = syncAndReadBack("rev-first", depotPath, "none", null,
				new AutoCleanImpl(true, true, false, false, false, Long.toString(c1), null));
		assertStartsWith(BOM, atFirst);
		assertArrayEquals(rev1, atFirst, "backward revision must carry its BOM verbatim");
	}

	@Test
	void testParallelSyncWithBom() throws Exception {
		// Parallel transfer threads - a separate streaming path from the serial sync.
		String base = "//depot/bompar";
		byte[][] contents = new byte[6][];
		for (int i = 0; i < contents.length; i++) {
			contents[i] = concat(BOM, ("parallel body " + i + "\n").getBytes(StandardCharsets.UTF_8));
			submitFileAsType(base + "/file_" + i + ".txt", contents[i], "text+D");
		}

		ParallelSync parallel = new ParallelSync(true, null, "4", "1", "1");
		Populate populate = new AutoCleanImpl(true, true, false, false, false, null, parallel);

		FreeStyleBuild build = buildDirSync("bom-par", base + "/...", "none", null, populate);

		for (int i = 0; i < contents.length; i++) {
			byte[] synced = readWorkspaceBytes(build, "file_" + i + ".txt");
			assertStartsWith(BOM, synced);
			assertArrayEquals(contents[i], synced, "parallel-synced file " + i + " must be verbatim");
		}
	}

	// ==================================================================
	// §F - idempotency / polling
	// ==================================================================

	@Test
	void testSecondAutoCleanBuildStable() throws Exception {
		String depotPath = "//depot/bomidem/f1.txt";
		byte[] content = concat(BOM, "idempotent body\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text+D");

		FreeStyleProject project = fileSyncProject("bom-f1", depotPath, "none", null, new AutoCleanImpl());

		FreeStyleBuild first = build(project);
		assertArrayEquals(content, readWorkspaceBytes(first, "f1.txt"), "first sync must be verbatim");

		FreeStyleBuild second = build(project);
		assertArrayEquals(content, readWorkspaceBytes(second, "f1.txt"), "second sync must remain verbatim");
	}

	@Test
	void testPollingReportsNoChange() throws Exception {
		String depotPath = "//depot/bomidem/f2.txt";
		byte[] content = concat(BOM, "poll body\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text+D");

		FreeStyleProject project = fileSyncProject("bom-f2", depotPath, "none", null, new AutoCleanImpl());
		FreeStyleBuild built = build(project);
		assertArrayEquals(content, readWorkspaceBytes(built, "f2.txt"));

		LogTaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
		PollingResult poll = project.poll(listener);
		assertEquals(PollingResult.NO_CHANGES, poll, "an untouched BOM file must not poll dirty");
	}

	@Test
	void testForceCleanTwiceStable() throws Exception {
		String depotPath = "//depot/bomidem/f3.txt";
		byte[] content = concat(BOM, "force clean idempotent\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text+D");

		FreeStyleProject project = fileSyncProject("bom-f3", depotPath, "none", null,
				new ForceCleanImpl(false, false, null, null));

		FreeStyleBuild first = build(project);
		assertArrayEquals(content, readWorkspaceBytes(first, "f3.txt"));

		FreeStyleBuild second = build(project);
		assertArrayEquals(content, readWorkspaceBytes(second, "f3.txt"), "repeat force-clean must be verbatim");
	}

	// ==================================================================
	// §G - twin-risk on a unicode server (must not break legit BOM management)
	// ==================================================================

	@Test
	void testUnicodeServerSingleBom() throws Exception {
		p4d.unicode();
		String depotPath = "//depot/bomuni/single.txt";
		byte[] content = concat(BOM, "hello world\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "utf8", "utf8-bom");

		byte[] synced = syncAndReadBack("uni-single", depotPath, "utf8-bom", null, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertEquals(1, countOccurrences(synced, BOM), "must be exactly one BOM, not doubled");
	}

	@Test
	void testUnicodeServerNoBomClientAddsBom() throws Exception {
		// utf8 file with no BOM; utf8-bom client -> Perforce adds exactly one BOM.
		p4d.unicode();
		String depotPath = "//depot/bomuni/addbom.txt";
		byte[] content = "no bom here\n".getBytes(StandardCharsets.UTF_8);

		submitFileAsType(depotPath, content, "utf8", "utf8");

		byte[] synced = syncAndReadBack("uni-addbom", depotPath, "utf8-bom", null, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertEquals(1, countOccurrences(synced, BOM), "exactly one BOM must be added");
	}

	@Test
	void testUnicodeServerUtf16SingleBom() throws Exception {
		// utf16 client file must carry exactly one correct-endian UTF-16 BOM.
		p4d.unicode();
		String depotPath = "//depot/bomuni/utf16.txt";
		byte[] content = "hello\n".getBytes(StandardCharsets.UTF_16); // BE with BOM

		submitFileAsType(depotPath, content, "utf16", "utf16");

		byte[] synced = syncAndReadBack("uni-utf16", depotPath, "utf16", null, new AutoCleanImpl());
		byte[] le = {(byte) 0xFF, (byte) 0xFE};
		byte[] be = {(byte) 0xFE, (byte) 0xFF};
		assertTrue(startsWith(le, synced) || startsWith(be, synced), "must start with a UTF-16 BOM");
		assertEquals(1, countOccurrences(synced, le) + countOccurrences(synced, be), "exactly one UTF-16 BOM");
		assertTrue(new String(synced, StandardCharsets.UTF_16).contains("hello"), "content must decode");
	}

	@Test
	void testUnicodeServerCrossCharsetNoDoubleBom() throws Exception {
		// Cross-charset round trip: submit under utf8-bom, sync under utf8. The fix must
		// not introduce a doubled BOM or corrupt the body (this server manages the
		// workspace BOM itself, so we assert integrity, not strip/add).
		p4d.unicode();
		String depotPath = "//depot/bomuni/cross.txt";
		String body = "cross charset body\n";
		byte[] content = concat(BOM, body.getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "utf8", "utf8-bom");

		byte[] synced = syncAndReadBack("uni-cross", depotPath, "utf8", null, new AutoCleanImpl());
		assertTrue(countOccurrences(synced, BOM) <= 1, "BOM must never be doubled");
		assertEquals(body, stripUtf8Bom(synced), "body must survive the cross-charset round trip");
	}

	@Test
	void testUnicodeServerBinaryBypass() throws Exception {
		// Binary files are not charset-translated even on a unicode server.
		p4d.unicode();
		String depotPath = "//depot/bomuni/binary.bin";
		byte[] content = concat(BOM, new byte[]{0x00, 0x01, (byte) 0xFF, 0x02});

		submitFileAsType(depotPath, content, "binary", "utf8");

		byte[] synced = syncAndReadBack("uni-binary", depotPath, "utf8", null, new AutoCleanImpl());
		assertArrayEquals(content, synced, "binary must be byte-for-byte identical on a unicode server");
	}

	// ==================================================================
	// §H - adjacent write/read paths
	// ==================================================================

	@Test
	void testShelveUnshelveRoundTripWithBom() throws Exception {
		// A text+D+BOM file shelved and then unshelved into a fresh client must round-trip
		// verbatim (the shelve/unshelve transfer path, not sync).
		String depotPath = "//depot/bomadj/shelf.txt";
		byte[] content = concat(BOM, "shelved body\n".getBytes(StandardCharsets.UTF_8));

		int shelf = shelveFileAsType(depotPath, content, "text+D");
		byte[] unshelved = unshelveAndReadBack("bom-unshelve", depotPath, shelf);

		assertStartsWith(BOM, unshelved);
		assertArrayEquals(content, unshelved, "unshelved BOM'd file must be verbatim");
	}

	@Test
	void testPrintReadsBomVerbatim() throws Exception {
		// p4 print (getFileContents) of a BOM'd file must return the exact bytes -
		// this is the path used by Pipeline-from-SCM / P4SCMFile reads.
		String depotPath = "//depot/bomadj/print.txt";
		byte[] content = concat(BOM, "printed body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");

		byte[] printed = printBytes(depotPath);
		assertStartsWith(BOM, printed);
		assertArrayEquals(content, printed, "printed BOM'd file must be verbatim");
	}

	@Test
	void testReconcileSeesNoEditForBom() throws Exception {
		// After a clean sync, reconcile/status must NOT think the BOM'd file was edited -
		// otherwise the workspace copy differs from the depot (a corrupted transfer).
		String depotPath = "//depot/bomadj/reconcile.txt";
		byte[] content = concat(BOM, "reconcile body\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");

		assertFalse(reconcileFindsChange(depotPath), "a correctly synced BOM file must show no phantom edit");
	}

	// ==================================================================
	// Helpers - submit / edit / shelve
	// ==================================================================

	/**
	 * Submit a file with raw bytes and a forced Perforce filetype. Adds the file
	 * (default auto-typing), reopens it to the requested type, then submits - so BOM'd
	 * content that would auto-detect as utf8 is reclassified.
	 */
	private long submitFileAsType(String depotPath, byte[] content, String p4type, String charset) throws Exception {
		String filename = fileName(depotPath);
		String client = "bom-submit.ws";
		String clientPath = "//" + client + "/" + filename;
		String view = "\"" + depotPath + "\" " + clientPath;

		ManualWorkspaceImpl workspace = manualClient(client, view, charset, "target/bom-submit.ws");
		File file = writeClientFile("target/bom-submit.ws", filename, content);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath);

			iclient.addFiles(files, new AddFilesOptions());
			reopenType(iclient, files, p4type);
			Changelist change = newChange(iclient, "BOM test submit " + depotPath, files);
			change.refresh();
			change.submit(false);
			return change.getId();
		} finally {
			file.delete();
		}
	}

	/** Non-unicode ("none" charset) submit. */
	private long submitFileAsType(String depotPath, byte[] content, String p4type) throws Exception {
		return submitFileAsType(depotPath, content, p4type, "none");
	}

	/** Open an existing depot file for edit, overwrite with raw bytes, submit a new rev. */
	private long editFileAsType(String depotPath, byte[] content, String p4type) throws Exception {
		String filename = fileName(depotPath);
		String client = "bom-submit.ws";
		String clientPath = "//" + client + "/" + filename;
		String view = "\"" + depotPath + "\" " + clientPath;

		ManualWorkspaceImpl workspace = manualClient(client, view, "none", "target/bom-submit.ws");
		File file = new File(new File("target/bom-submit.ws").getAbsoluteFile(), filename);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath);

			iclient.sync(files, new SyncOptions());
			iclient.editFiles(files, new EditFilesOptions());

			file.delete();
			Files.write(file.toPath(), content);

			reopenType(iclient, files, p4type);
			Changelist change = newChange(iclient, "BOM test edit " + depotPath, files);
			change.refresh();
			change.submit(false);
			return change.getId();
		} finally {
			file.delete();
		}
	}

	/** Add a file with a forced type, shelve it in a pending change, return the change id. */
	private int shelveFileAsType(String depotPath, byte[] content, String p4type) throws Exception {
		String filename = fileName(depotPath);
		String client = "bom-shelve.ws";
		String clientPath = "//" + client + "/" + filename;
		String view = "\"" + depotPath + "\" " + clientPath;

		ManualWorkspaceImpl workspace = manualClient(client, view, "none", "target/bom-shelve.ws");
		File file = writeClientFile("target/bom-shelve.ws", filename, content);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath);

			iclient.addFiles(files, new AddFilesOptions());
			reopenType(iclient, files, p4type);
			Changelist change = newChange(iclient, "BOM test shelve " + depotPath, files);
			iclient.shelveFiles(files, change.getId(), new ShelveFilesOptions());
			return change.getId();
		} finally {
			file.delete();
		}
	}

	/** Unshelve a shelved change into a fresh client and read the file bytes back. */
	private byte[] unshelveAndReadBack(String clientBase, String depotPath, int shelf) throws Exception {
		String filename = fileName(depotPath);
		String client = clientBase + ".ws";
		String clientPath = "//" + client + "/" + filename;
		String view = "\"" + depotPath + "\" " + clientPath;
		String root = "target/" + client;

		ManualWorkspaceImpl workspace = manualClient(client, view, "none", root);
		File file = new File(new File(root).getAbsoluteFile(), filename);
		file.delete();

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath);
			iclient.unshelveFiles(files, shelf, IChangelist.DEFAULT, new UnshelveFilesOptions());
			return Files.readAllBytes(file.toPath());
		}
	}

	/** {@code p4 print} of a depot file, returned as raw bytes. */
	private byte[] printBytes(String depotPath) throws Exception {
		String client = "bom-print.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(depotPath);
			GetFileContentsOptions opts = new GetFileContentsOptions();
			opts.setNoHeaderLine(true);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = p4.getConnection().getFileContents(files, opts)) {
				copy(in, out);
			}
			return out.toByteArray();
		}
	}

	/** Sync a file into a fresh client, then reconcile; true if reconcile opens anything. */
	private boolean reconcileFindsChange(String depotPath) throws Exception {
		String filename = fileName(depotPath);
		String client = "bom-recon.ws";
		String clientPath = "//" + client + "/" + filename;
		String view = "\"" + depotPath + "\" " + clientPath;

		ManualWorkspaceImpl workspace = manualClient(client, view, "none", "target/bom-recon.ws");

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			IClient iclient = p4.getClient();
			List<IFileSpec> files = FileSpecBuilder.makeFileSpecList(clientPath);
			iclient.sync(files, new SyncOptions());

			ReconcileFilesOptions opts = new ReconcileFilesOptions();
			opts.setUseWildcards(true);
			opts.setOutsideAdd(true);
			opts.setOutsideEdit(true);
			for (IFileSpec s : iclient.reconcileFiles(files, opts)) {
				if (s.getOpStatus() == FileSpecOpStatus.VALID) {
					return true;
				}
			}
			return false;
		}
	}

	/** {@code p4 fstat -T headType} for a single depot file. */
	private String getHeadType(String depotPath) throws Exception {
		String client = "bom-stat.ws";
		String view = "//depot/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(view, null);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", false, client, spec, false);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, null, workspace)) {
			List<IFileSpec> fileSpec = FileSpecBuilder.makeFileSpecList(depotPath);
			GetExtendedFilesOptions opts = new GetExtendedFilesOptions();
			List<IExtendedFileSpec> eSpec = p4.getConnection().getExtendedFiles(fileSpec, opts);
			return eSpec.get(0).getHeadType();
		}
	}

	// ==================================================================
	// Helpers - jobs / read
	// ==================================================================

	/** Freestyle project syncing a single depot file into its workspace. */
	private FreeStyleProject fileSyncProject(String jobName, String depotPath, String charset, String line, Populate populate) throws Exception {
		String client = jobName + ".ws";
		String view = "\"" + depotPath + "\" //" + client + "/" + fileName(depotPath);
		WorkspaceSpec spec = workspaceSpec(view, line);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl(charset, false, client, spec, false);

		FreeStyleProject project = jenkins.createFreeStyleProject(jobName);
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();
		return project;
	}

	/** Build and read a single-file sync workspace back as bytes. */
	private byte[] syncAndReadBack(String jobName, String depotPath, String charset, String line, Populate populate) throws Exception {
		FreeStyleBuild build = build(fileSyncProject(jobName, depotPath, charset, line, populate));
		return readWorkspaceBytes(build, fileName(depotPath));
	}

	/** Non-unicode, default line-ending convenience overload. */
	private byte[] syncAndReadBack(String jobName, String depotPath, Populate populate) throws Exception {
		return syncAndReadBack(jobName, depotPath, "none", null, populate);
	}

	/** Build a freestyle job syncing a whole depot view (multi-file) and return the build. */
	private FreeStyleBuild buildDirSync(String jobName, String depotView, String charset, String line, Populate populate) throws Exception {
		String client = jobName + ".ws";
		String view = depotView + " //" + client + "/...";
		WorkspaceSpec spec = workspaceSpec(view, line);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl(charset, false, client, spec, false);

		FreeStyleProject project = jenkins.createFreeStyleProject(jobName);
		project.setScm(new PerforceScm(CREDENTIAL, workspace, populate));
		project.save();
		return build(project);
	}

	private FreeStyleBuild build(FreeStyleProject project) throws Exception {
		FreeStyleBuild b = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		assertEquals(Result.SUCCESS, b.getResult(), "sync build should succeed");
		return b;
	}

	private byte[] readWorkspaceBytes(FreeStyleBuild build, String relPath) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = build.getWorkspace().child(relPath).read()) {
			copy(in, out);
		}
		return out.toByteArray();
	}

	// ==================================================================
	// Helpers - low level
	// ==================================================================

	private ManualWorkspaceImpl manualClient(String client, String view, String charset, String rootPath) {
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false,
				null, "LOCAL", view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl(charset, true, client, spec, false);
		workspace.setExpand(new HashMap<>());
		File wsRoot = new File(rootPath).getAbsoluteFile();
		wsRoot.mkdirs();
		workspace.setRootPath(wsRoot.toString());
		return workspace;
	}

	private WorkspaceSpec workspaceSpec(String view, String line) {
		return (line == null)
				? new WorkspaceSpec(view, null)
				: new WorkspaceSpec(true, true, false, false, false, false, null, line, view, null, null, null, true);
	}

	private File writeClientFile(String rootPath, String filename, byte[] content) throws Exception {
		File wsRoot = new File(rootPath).getAbsoluteFile();
		wsRoot.mkdirs();
		File file = new File(wsRoot, filename);
		file.delete();
		Files.write(file.toPath(), content);
		return file;
	}

	private void reopenType(IClient iclient, List<IFileSpec> files, String p4type) throws Exception {
		ReopenFilesOptions retype = new ReopenFilesOptions();
		retype.setFileType(p4type);
		iclient.reopenFiles(files, retype);
	}

	private Changelist newChange(IClient iclient, String desc, List<IFileSpec> files) throws Exception {
		Changelist change = new Changelist();
		change.setDescription(desc);
		change = (Changelist) iclient.createChangelist(change);
		ReopenFilesOptions toChange = new ReopenFilesOptions();
		toChange.setChangelistId(change.getId());
		iclient.reopenFiles(files, toChange);
		return change;
	}

	private static void copy(InputStream in, ByteArrayOutputStream out) throws Exception {
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) != -1) {
			out.write(buf, 0, n);
		}
	}

	private static String fileName(String depotPath) {
		return depotPath.substring(depotPath.lastIndexOf('/') + 1);
	}

	/** Decode UTF-8 bytes to a String, dropping a single leading 3-byte BOM if present. */
	private static String stripUtf8Bom(byte[] bytes) {
		byte[] payload = startsWith(BOM, bytes)
				? java.util.Arrays.copyOfRange(bytes, BOM.length, bytes.length)
				: bytes;
		return new String(payload, StandardCharsets.UTF_8);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private static boolean startsWith(byte[] prefix, byte[] actual) {
		if (actual.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (actual[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static void assertStartsWith(byte[] prefix, byte[] actual) {
		assertTrue(startsWith(prefix, actual), "expected content to start with the given byte prefix");
	}

	private static int countOccurrences(byte[] haystack, byte[] needle) {
		int count = 0;
		outer:
		for (int i = 0; i + needle.length <= haystack.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			count++;
		}
		return count;
	}
}
