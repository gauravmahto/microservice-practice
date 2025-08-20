package com.example;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies /run-check endpoint responds with 503 when k8s integration disabled.
 */
public class RunCheckEndpointTest {
  private static WebServer server;
  private static HttpClient client;

  @BeforeAll
  static void start() {
    System.setProperty("k8s.disabled", "true");
    server = SimpleWebServer.startServer(Config.create(), 0);
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stop() {
    if (server != null)
      server.shutdown().await();
  }

  @Test
  void runCheckDisabledReturns503() throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + server.port() + "/run-check"))
        .timeout(Duration.ofSeconds(5))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(503, resp.statusCode(), "Expected 503 when k8s disabled, got body: " + resp.body());
    assertTrue(resp.body().contains("not available"));
  }
}
