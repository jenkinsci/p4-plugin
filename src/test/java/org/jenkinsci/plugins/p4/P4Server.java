package org.jenkinsci.plugins.p4;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;

import com.cloudbees.plugins.credentials.CredentialsScope;

public class P4Server {

	private static Logger logger = Logger.getLogger(P4Server.class.getName());

	private ConnectionHelper p4;
	private final String p4d;
	private final String p4port;
	private final File p4root;

	public P4Server(String p4bin, String root, String p4port) {
		String p4d = p4bin;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			p4d += "bin.ntx64/p4d.exe";
		}
		if (os.contains("mac")) {
			p4d += "bin.darwin90x86_64/p4d";
		}
		if (os.contains("nix") || os.contains("nux")) {
			p4d += "bin.linux26x86_64/p4d";
		}
		this.p4d = p4d;
		this.p4root = new File(root);
		this.p4port = p4port;
	}

	public void start() throws Exception {
		CommandLine cmdLine = new CommandLine(p4d);
		// cmdLine.addArgument("-vserver=5");
		cmdLine.addArgument("-C1");
		cmdLine.addArgument("-r");
		cmdLine.addArgument(formatPath(p4root.getAbsolutePath()));
		cmdLine.addArgument("-p");
		cmdLine.addArgument(p4port);
		cmdLine.addArgument("-Llog");

		DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
		DefaultExecutor executor = new DefaultExecutor();
		executor.execute(cmdLine, resultHandler);
		logger.info("start signal sent...");

		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM, "id",
				"desc", p4port, null, "admin", "0", "0", "Password");

		int retry = 0;
		while (retry < 20) {
			try {
				p4 = new ConnectionHelper(auth);
				break;
			} catch (Exception e) {
				Thread.sleep(100);
			}
		}
	}

	public void stop() throws Exception {
		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM, "id",
				"desc", p4port, null, "admin", "0", "0", "Password");

		p4 = new ConnectionHelper(auth);
		p4.login();
		p4.stop();
		logger.info("stop signal sent...");

		int retry = 0;
		while (retry < 20) {
			try {
				if (!p4.login()) {
					break;
				} else {
					Thread.sleep(100);
				}
			} catch (Exception e) {
				break;
			}
		}
	}

	public void upgrade() throws Exception {
		exec(new String[] { "-xu" });
	}

	public void restore(File ckp) throws Exception {
		exec(new String[] { "-jr", "-z", formatPath(ckp.getAbsolutePath()) });
	}

	public void extract(File archive) throws Exception {
		TarArchiveInputStream tarIn = null;
		tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(
				new BufferedInputStream(new FileInputStream(archive))));

		TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
		while (tarEntry != null) {
			File node = new File(p4root, tarEntry.getName());
			logger.info("extracting: " + node.getCanonicalPath());
			if (tarEntry.isDirectory()) {
				node.mkdirs();
			} else {
				node.createNewFile();
				byte[] buf = new byte[1024];
				BufferedOutputStream bout = new BufferedOutputStream(
						new FileOutputStream(node));

				int len = 0;
				while ((len = tarIn.read(buf)) != -1) {
					bout.write(buf, 0, len);
				}

				bout.close();
				buf = null;
			}
			tarEntry = tarIn.getNextTarEntry();
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

	public int getVersion() throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CommandLine cmdLine = new CommandLine(p4d);
		cmdLine.addArgument("-V");
		DefaultExecutor executor = new DefaultExecutor();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		executor.setStreamHandler(streamHandler);
		executor.execute(cmdLine);

		int version = 0;
		for (String line : outputStream.toString().split("\\n")) {
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
		logger.info("P4D Version: " + version);
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

		logger.info("EXEC: " + cmdLine.toString());

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
