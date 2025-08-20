package com.example;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SimpleWebServer} verifying greeting and health
 * endpoints.
 */
public class SimpleWebServerTest {
  // WebServer instance started once for all tests
  private static WebServer server;
  // Shared HTTP client with short connect timeout
  private static HttpClient client;

  @BeforeAll
  static void start() {
    Config config = Config.create();
    // Use ephemeral port 0 so OS selects a free port, avoids hard-coded conflicts
    server = SimpleWebServer.startServer(config, 0);
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stop() {
    if (server != null) {
      // Gracefully shutdown server and wait for completion
      server.shutdown().await();
    }
  }

  @Test
  void rootEndpointReturnsGreeting() throws Exception {
    // Validate that root path returns a greeting string
    String body = get("/");
    assertTrue(body.contains("Hello") || body.contains("Hello from"), "Greeting not found: " + body);
  }

  @Test
  void healthEndpointsReturnUp() throws Exception {
    // Basic health/liveness checks should both report status UP
    String live = get("/health/live");
    String ready = get("/health/ready");
    assertTrue(live.contains("\"status\":\"UP\""), live);
    assertTrue(ready.contains("\"status\":\"UP\""), ready);
  }

  // Helper to perform a GET request and assert 200 OK, returning body
  private String get(String path) throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + server.port() + path))
        .timeout(Duration.ofSeconds(3))
        .GET()
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resp.statusCode(), "Non-OK status for " + path);
    return resp.body();
  }
}
