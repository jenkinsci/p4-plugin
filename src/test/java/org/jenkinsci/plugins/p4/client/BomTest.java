package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.option.client.AddFilesOptions;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.ReopenFilesOptions;
import com.perforce.p4java.option.client.ShelveFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.client.UnshelveFilesOptions;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
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
 * JENKINS-76454: a file whose content starts with a UTF-8 BOM ({@code EF BB BF}) must not
 * be corrupted when P4Java syncs it into a Jenkins workspace.
 *
 * <p>Each test submits raw bytes with a forced filetype, runs a real Jenkins operation,
 * and reads the bytes back to check the BOM and body are intact. Fixed in p4java
 * 2026.1.2989454 (fails on 2025.2.2917314).
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
	// 1. The reported repro (standalone for a clean 1:1 signal)
	// ==================================================================

	@Issue("JENKINS-76454")
	@Test
	void testTextDeltaWithBom() throws Exception {
		String depotPath = "//depot/bom/text_delta.txt";
		byte[] content = concat(BOM, "line1\nline2\n".getBytes(StandardCharsets.UTF_8));

		submitFileAsType(depotPath, content, "text+D");
		assertTrue(getHeadType(depotPath).startsWith("text"), "fixture should be a text filetype");

		byte[] synced = syncAndReadBack("text-delta", depotPath, new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertBodyEquals(content, synced, "text+D BOM file must transfer verbatim");
	}

	// ==================================================================
	// 2. All other filetypes + no-BOM controls + no cross-contamination,
	//    pulled together in a single force-clean (full re-transfer) sync.
	// ==================================================================

	@Test
	void testBomFiletypesAndControls() throws Exception {
		String base = "//depot/bomtypes";

		byte[] text = concat(BOM, "plain text body\n".getBytes(StandardCharsets.UTF_8));
		byte[] compressed = concat(BOM, "compressed body\n".getBytes(StandardCharsets.UTF_8));
		byte[] fullfile = concat(BOM, "full-file body\n".getBytes(StandardCharsets.UTF_8));
		byte[] exec = concat(BOM, "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8));
		byte[] writable = concat(BOM, "writable body\n".getBytes(StandardCharsets.UTF_8));
		byte[] keyword = concat(BOM, "$Id$\nkeyword body\n".getBytes(StandardCharsets.UTF_8));
		byte[] binary = concat(BOM, new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x7F, 0x00});
		byte[] plain = "plain ascii no bom\n".getBytes(StandardCharsets.UTF_8);
		byte[] bomOnly = BOM.clone();

		submitFileAsType(base + "/text.txt", text, "text");
		submitFileAsType(base + "/compressed.txt", compressed, "text+C");
		submitFileAsType(base + "/fullfile.txt", fullfile, "text+F");
		submitFileAsType(base + "/exec.txt", exec, "text+x");
		submitFileAsType(base + "/writable.txt", writable, "text+w");
		submitFileAsType(base + "/keyword.txt", keyword, "text+k");
		submitFileAsType(base + "/blob.bin", binary, "binary");
		submitFileAsType(base + "/plain.txt", plain, "text");
		submitFileAsType(base + "/bomonly.txt", bomOnly, "text");

		// Guard on the fixtures themselves.
		assertEquals("binary", getHeadType(base + "/blob.bin"));

		FreeStyleBuild build = buildDirSync("bom-types", base + "/...", "none", null,
				new ForceCleanImpl(false, false, null, null));

		// Every text filetype variant preserves the BOM and body verbatim.
		for (String f : new String[]{"text.txt", "compressed.txt", "fullfile.txt", "exec.txt", "writable.txt"}) {
			byte[] synced = readWorkspaceBytes(build, f);
			assertStartsWith(BOM, synced);
		}
		assertBodyEquals(text, readWorkspaceBytes(build, "text.txt"), "text must be verbatim");
		assertBodyEquals(compressed, readWorkspaceBytes(build, "compressed.txt"), "text+C must be verbatim");
		assertBodyEquals(fullfile, readWorkspaceBytes(build, "fullfile.txt"), "text+F must be verbatim");
		assertBodyEquals(exec, readWorkspaceBytes(build, "exec.txt"), "text+x must be verbatim");
		assertBodyEquals(writable, readWorkspaceBytes(build, "writable.txt"), "text+w must be verbatim");

		// text+k: BOM preserved AND the keyword expanded.
		byte[] syncedKeyword = readWorkspaceBytes(build, "keyword.txt");
		assertStartsWith(BOM, syncedKeyword);
		String keywordBody = new String(syncedKeyword, StandardCharsets.UTF_8);
		assertTrue(keywordBody.contains("$Id:"), "keyword $Id$ should have expanded: " + keywordBody);
		assertFalse(keywordBody.contains("$Id$"), "unexpanded keyword should not remain: " + keywordBody);

		// binary: BOM bytes are just data - verbatim.
		assertArrayEquals(binary, readWorkspaceBytes(build, "blob.bin"), "binary must be verbatim");

		// controls: no cross-contamination from BOM'd neighbours.
		byte[] syncedPlain = readWorkspaceBytes(build, "plain.txt");
		assertBodyEquals(plain, syncedPlain, "plain file must be verbatim");
		assertFalse(startsWith(BOM, syncedPlain), "plain file must not gain a BOM");

		// BOM-only file must survive as exactly 3 bytes.
		assertArrayEquals(BOM, readWorkspaceBytes(build, "bomonly.txt"), "BOM-only file must stay 3 bytes");
	}

	// ==================================================================
	// 3. Sync-only + parallel transfer + a large multibyte body.
	// ==================================================================

	@Test
	void testSyncOnlyParallelLargeBom() throws Exception {
		String base = "//depot/bompar";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 120_000; i++) {
			sb.append("café über naïve — 日本語 テスト résumé\n");
		}
		byte[] large = concat(BOM, sb.toString().getBytes(StandardCharsets.UTF_8));
		submitFileAsType(base + "/large.txt", large, "text");

		byte[][] small = new byte[4][];
		for (int i = 0; i < small.length; i++) {
			small[i] = concat(BOM, ("parallel body " + i + "\n").getBytes(StandardCharsets.UTF_8));
			submitFileAsType(base + "/file_" + i + ".txt", small[i], "text+D");
		}

		ParallelSync parallel = new ParallelSync(true, null, "4", "1", "1");
		Populate populate = new SyncOnlyImpl(false, true, false, false, null, parallel);

		FreeStyleBuild build = buildDirSync("bom-syncpar", base + "/...", "none", null, populate);

		byte[] syncedLarge = readWorkspaceBytes(build, "large.txt");
		assertStartsWith(BOM, syncedLarge);
		assertBodyEquals(large, syncedLarge, "large multibyte BOM file must transfer verbatim");

		for (int i = 0; i < small.length; i++) {
			byte[] synced = readWorkspaceBytes(build, "file_" + i + ".txt");
			assertStartsWith(BOM, synced);
			assertBodyEquals(small[i], synced, "parallel-synced file " + i + " must be verbatim");
		}
	}

	// ==================================================================
	// 4. An unchanged BOM file must not be seen as modified by reconcile
	//    or polling - a corrupted BOM would surface as a phantom edit.
	// ==================================================================

	@Test
	void testUnchangedBomNotDetectedAsModified() throws Exception {
		String depotPath = "//depot/bomidem/unchanged.txt";
		byte[] content = concat(BOM, "unchanged body\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text+D");

		// Polling: after a clean sync, nothing changed -> must not request a build.
		FreeStyleProject project = fileSyncProject("bom-idem", depotPath, "none", null, new AutoCleanImpl());
		FreeStyleBuild built = build(project);
		byte[] synced = readWorkspaceBytes(built, "unchanged.txt");
		assertStartsWith(BOM, synced);
		assertBodyEquals(content, synced, "sync must be verbatim");

		PollingResult poll = project.poll(new LogTaskListener(LOGGER, Level.INFO));
		assertEquals(PollingResult.NO_CHANGES, poll, "an untouched BOM file must not poll dirty");

		// Reconcile: a freshly synced BOM file must not look edited.
		assertFalse(reconcileFindsChange(depotPath), "a correctly synced BOM file must show no phantom edit");
	}

	// ==================================================================
	// 5. Line-ending translation must not disturb the BOM.
	// ==================================================================

	@Test
	void testWinLineEndPreservesBom() throws Exception {
		String depotPath = "//depot/bom/win_eol.txt";
		byte[] content = concat(BOM, "line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(depotPath, content, "text");

		byte[] synced = syncAndReadBack("win-eol", depotPath, "none", "WIN", new AutoCleanImpl());
		assertStartsWith(BOM, synced);
		assertEquals(3, countOccurrences(synced, new byte[]{'\r', '\n'}), "each newline should become CRLF");
	}

	// ==================================================================
	// 6. Twin-risk: legitimate BOM management on a unicode server must not
	//    break (no doubled BOM). Covers utf8 and utf16 filetypes.
	// ==================================================================

	@Test
	void testUnicodeServerBomManagement() throws Exception {
		p4d.unicode();

		// utf8 file with a BOM, synced by a utf8-bom client -> exactly one BOM.
		String utf8Path = "//depot/bomuni/utf8.txt";
		byte[] utf8 = concat(BOM, "hello world\n".getBytes(StandardCharsets.UTF_8));
		submitFileAsType(utf8Path, utf8, "utf8", "utf8-bom");

		byte[] syncedUtf8 = syncAndReadBack("uni-utf8", utf8Path, "utf8-bom", null, new AutoCleanImpl());
		assertStartsWith(BOM, syncedUtf8);
		assertEquals(1, countOccurrences(syncedUtf8, BOM), "utf8 must have exactly one BOM, not doubled");

		// utf16 file -> exactly one correct-endian UTF-16 BOM.
		String utf16Path = "//depot/bomuni/utf16.txt";
		byte[] utf16 = "hello\n".getBytes(StandardCharsets.UTF_16); // BE with BOM
		submitFileAsType(utf16Path, utf16, "utf16", "utf16");

		byte[] syncedUtf16 = syncAndReadBack("uni-utf16", utf16Path, "utf16", null, new AutoCleanImpl());
		byte[] le = {(byte) 0xFF, (byte) 0xFE};
		byte[] be = {(byte) 0xFE, (byte) 0xFF};
		assertTrue(startsWith(le, syncedUtf16) || startsWith(be, syncedUtf16), "utf16 must start with a UTF-16 BOM");
		assertEquals(1, countOccurrences(syncedUtf16, le) + countOccurrences(syncedUtf16, be),
				"utf16 must have exactly one BOM");
		assertTrue(new String(syncedUtf16, StandardCharsets.UTF_16).contains("hello"), "utf16 content must decode");
	}

	// ==================================================================
	// 7. Shelve/unshelve is a distinct write path from sync.
	// ==================================================================

	@Test
	void testShelveUnshelveRoundTripWithBom() throws Exception {
		String depotPath = "//depot/bomadj/shelf.txt";
		byte[] content = concat(BOM, "shelved body\n".getBytes(StandardCharsets.UTF_8));

		int shelf = shelveFileAsType(depotPath, content, "text+D");
		byte[] unshelved = unshelveAndReadBack("bom-unshelve", depotPath, shelf);

		assertStartsWith(BOM, unshelved);
		assertBodyEquals(content, unshelved, "unshelved BOM file must be verbatim");
	}

	// ==================================================================
	// Helpers - submit / shelve / unshelve / stat / reconcile
	// ==================================================================

	/**
	 * Submit a file with raw bytes and a forced Perforce filetype. Adds the file
	 * (default auto-typing), reopens it to the requested type, then submits - so BOM'd
	 * content that would auto-detect as utf8 is reclassified.
	 *
	 * @param charset connection charset ("none" for a non-unicode server, or e.g.
	 *                "utf8", "utf8-bom", "utf16" for a unicode server).
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

	/**
	 * Verbatim comparison for <em>text</em> content, ignoring CR bytes. A text file's line
	 * endings are translated by the client platform on sync (LF on Linux, CRLF on
	 * Windows), so a byte-for-byte compare against LF-based expected content would fail on
	 * a Windows agent even though the file is intact. Stripping CR makes the assertion
	 * platform-independent while still catching any real body corruption. The BOM itself
	 * is checked separately via {@link #assertStartsWith}. Do NOT use this for binary
	 * content (which may legitimately contain 0x0D and is never EOL-translated).
	 */
	private static void assertBodyEquals(byte[] expected, byte[] actual, String message) {
		assertArrayEquals(stripCr(expected), stripCr(actual), message);
	}

	private static byte[] stripCr(byte[] in) {
		byte[] out = new byte[in.length];
		int n = 0;
		for (byte b : in) {
			if (b != '\r') {
				out[n++] = b;
			}
		}
		byte[] trimmed = new byte[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
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
