package org.jenkinsci.plugins.p4;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DummyServerTest {

	@Test
	void testRespondsOnlyAfterFullyDrainingASlowlyWrittenPostBody() throws Exception {
		DummyServer server = new DummyServer(0);
		Thread thread = new Thread(server);
		thread.setDaemon(true);
		thread.start();

		URL url = new URL("http://localhost:" + server.getPort() + "/");
		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.setDoOutput(true);
		http.setRequestMethod("POST");
		http.connect();

		// Write the body in small, slow chunks. If the server responds and closes the
		// socket before reading the whole request (the bug this stub used to have), the
		// client sees a connection reset partway through this loop instead of a clean
		// response.
		byte[] body = "x".repeat(50_000).getBytes(StandardCharsets.UTF_8);
		try (OutputStream os = http.getOutputStream()) {
			for (int i = 0; i < body.length; i += 1000) {
				int end = Math.min(i + 1000, body.length);
				os.write(body, i, end - i);
				os.flush();
				Thread.sleep(1);
			}
		}

		assertEquals(200, http.getResponseCode());
	}
}
