package org.jenkinsci.plugins.p4;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class DummyServer implements Runnable {

	private final ServerSocket server;

	public DummyServer(int port) throws Exception {
		server = new ServerSocket(port);
	}

	public void run() {
		while (true) {
			try {
				Socket socket = server.accept();
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
}
