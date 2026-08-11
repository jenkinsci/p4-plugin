package org.jenkinsci.plugins.p4;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A minimal JDK-native (no new dependency) HTTP server for tests that need to stand in for a
 * real JSON REST API, such as Perforce Swarm. Register a canned response per exact request
 * path with {@link #stub}; anything else gets a 404.
 */
public class JsonHttpStubServer implements AutoCloseable {

	private final HttpServer server;
	private final Map<String, Response> routes = new HashMap<>();
	private final Map<String, String> lastRequestBody = new HashMap<>();

	public JsonHttpStubServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	public int getPort() {
		return server.getAddress().getPort();
	}

	public String getUrl() {
		return "http://localhost:" + getPort();
	}

	public void stub(String path, int status, String jsonBody) {
		routes.put(path, new Response(status, jsonBody));
	}

	/**
	 * The body of the most recent request received at the given path, or null if none yet.
	 */
	public String getLastRequestBody(String path) {
		return lastRequestBody.get(path);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		lastRequestBody.put(path, body);

		Response response = routes.get(path);
		if (response == null) {
			response = new Response(404, "{\"error\":\"no stub for " + exchange.getRequestURI() + "\"}");
		}
		byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(response.status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private record Response(int status, String body) {
	}
}
