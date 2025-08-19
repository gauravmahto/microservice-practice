package com.example;

import io.helidon.microprofile.server.Server;
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
  private static Server server;
  private static HttpClient client;

  @BeforeAll
  static void start() {
    System.setProperty("k8s.disabled", "true");
    System.setProperty("server.port", "0");
    server = Server.builder().addApplication(ApplicationConfig.class).port(0).build().start();
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stop() {
    if (server != null)
      server.stop();
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
