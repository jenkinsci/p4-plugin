package org.jenkinsci.plugins.p4;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleTestServer {

	private static Logger logger = LoggerFactory.getLogger(SimpleTestServer.class);

	private static final String RESOURCES = "src/test/resources/";

	private final String p4d;
	private final File p4root;
	private final String p4ver;

	public SimpleTestServer(String root, String version) {
		String p4d = new File(RESOURCES + version).getAbsolutePath();
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			p4d += "/bin.ntx64/p4d.exe";
		}
		if (os.contains("mac")) {
			p4d += "/bin.darwin90x86_64/p4d";
		}
		if (os.contains("nix") || os.contains("nux")) {
			p4d += "/bin.linux26x86_64/p4d";
		}
		this.p4d = p4d;
		this.p4root = new File("target/" + root).getAbsoluteFile();
		this.p4ver = version;
	}

	public String getResources() {
		return RESOURCES + p4ver;
	}

	public String getRshPort() {
		String rsh = "rsh:" + p4d;
		rsh += " -r " + p4root;
		rsh += " -L log";
		rsh += " -i ";

		return rsh;
	}
	public String getLogPath() {
		return p4root + File.separator + "log";
	}

	public void upgrade() throws Exception {
		exec(new String[] { "-xu" });
	}

	public void unicode() throws Exception {
		exec(new String[] { "-xi" });
	}

	public void restore(File ckp) throws Exception {
		exec(new String[] { "-jr", "-z", formatPath(ckp.getAbsolutePath()) });
	}

	public void extract(File archive) throws Exception {
		TarArchiveInputStream tarIn = null;
		tarIn = new TarArchiveInputStream(
				new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(archive))));

		TarArchiveEntry tarEntry = tarIn.getNextEntry();
		while (tarEntry != null) {
			File node = new File(p4root, tarEntry.getName());
			logger.debug("extracting: {}", node.getCanonicalPath());
			if (tarEntry.isDirectory()) {
				node.mkdirs();
			} else {
				node.createNewFile();
				byte[] buf = new byte[1024];
				BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(node));

				int len = 0;
				while ((len = tarIn.read(buf)) != -1) {
					bout.write(buf, 0, len);
				}
				bout.close();
			}
			tarEntry = tarIn.getNextEntry();
		}
		tarIn.close();
	}

	public void clean() throws IOException {
		if (p4root.exists()) {
			FileUtils.cleanDirectory(p4root);
		} else {
			p4root.mkdir();
		}
	}

	public void destroy() throws Exception {
		if (p4root.exists()) {
			int count = 5;
			while(!tryDestroy() && count > 0) {
				Thread.sleep(200);
				count --;
			}
		}
	}

	private boolean tryDestroy() {
		try {
			FileUtils.deleteDirectory(p4root);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public int getVersion() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CommandLine cmdLine = new CommandLine(p4d);
		cmdLine.addArgument("-V");
		DefaultExecutor executor = new DefaultExecutor();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		executor.setStreamHandler(streamHandler);
		executor.execute(cmdLine);

		int version = 0;
		for (String line : outputStream.toString(StandardCharsets.UTF_8).split("\\n")) {
			if (line.startsWith("Rev. P4D")) {
				Pattern p = Pattern.compile("\\d{4}\\.\\d{1}");
				Matcher m = p.matcher(line);
				while (m.find()) {
					String found = m.group();
					found = found.replace(".", ""); // strip "."
					version = Integer.parseInt(found);
				}
			}
		}
		logger.info("P4D Version: {}", version);
		return version;
	}

	private int exec(String[] args) throws Exception {
		CommandLine cmdLine = new CommandLine(p4d);
		cmdLine.addArgument("-C1");
		cmdLine.addArgument("-r");
		cmdLine.addArgument(formatPath(p4root.getAbsolutePath()));
		for (String arg : args) {
			cmdLine.addArgument(arg);
		}

		logger.debug("EXEC: {}", cmdLine);

		DefaultExecutor executor = new DefaultExecutor();
		return executor.execute(cmdLine);
	}

	private String formatPath(String path) {
		final String Q = "\"";
		path = Q + path + Q;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			path = path.replace('\\', '/');
		}
		return path;
	}
}
