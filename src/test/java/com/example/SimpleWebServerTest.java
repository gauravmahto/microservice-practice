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

public class SimpleWebServerTest {
  private static WebServer server;
  private static HttpClient client;

  @BeforeAll
  static void start() {
    Config config = Config.create();
    // Use ephemeral port 0; Helidon will choose a free one
    server = SimpleWebServer.startServer(config, 0);
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stop() {
    if (server != null) {
      server.shutdown().await();
    }
  }

  @Test
  void rootEndpointReturnsGreeting() throws Exception {
    String body = get("/");
    assertTrue(body.contains("Hello") || body.contains("Hello from"), "Greeting not found: " + body);
  }

  @Test
  void healthEndpointsReturnUp() throws Exception {
    String live = get("/health/live");
    String ready = get("/health/ready");
    assertTrue(live.contains("\"status\":\"UP\""), live);
    assertTrue(ready.contains("\"status\":\"UP\""), ready);
  }

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
