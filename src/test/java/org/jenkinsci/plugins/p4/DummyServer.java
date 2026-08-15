package org.jenkinsci.plugins.p4;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DummyServer implements Runnable {

	private final ServerSocket server;

	public DummyServer(int port) throws Exception {
		server = new ServerSocket(port);
	}

	public int getPort() {
		return server.getLocalPort();
	}

	public void run() {
		while (true) {
			try {
				Socket socket = server.accept();
				// Drain the request (headers + any body) before responding. Responding and
				// closing while the client is still writing a POST body can reset the
				// connection before the client ever reads the response - reproducible on
				// Linux, not observed on macOS.
				readRequest(socket);

				OutputStreamWriter osw;
				osw = new OutputStreamWriter(socket.getOutputStream());
				BufferedWriter out = new BufferedWriter(osw);

				out.write("HTTP/1.0 200 OK\r\n");
				out.write("\r\n");
				out.close();
				socket.close();
			} catch (IOException e) {
			}
		}
	}

	private void readRequest(Socket socket) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		int contentLength = 0;
		String line;
		while ((line = in.readLine()) != null && !line.isEmpty()) {
			if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
				contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
			}
		}
		for (int i = 0; i < contentLength; i++) {
			if (in.read() == -1) {
				break;
			}
		}
	}
}
