package com.example;

// Helidon configuration API (loads application.yaml / system / env)
import io.helidon.config.Config;
// Core web server & routing types
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
// Health aggregation & built‑in health checks
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.health.checks.DeadlockHealthCheck;
// MicroProfile Health SPI for custom readiness check
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple Helidon SE application demonstrating:
 * <ul>
 * <li>Config-driven port & greeting (application.yaml: server.port,
 * app.greeting)</li>
 * <li>Health endpoints: /health, /health/live, /health/ready via
 * HealthSupport</li>
 * <li>Custom readiness flag toggled after successful server start</li>
 * <li>Built-in liveness checks (heap memory & deadlock detection)</li>
 * <li>Sample endpoints: '/' returns greeting + timestamp, '/config' dumps
 * config (demo only)</li>
 * </ul>
 */
public class SimpleWebServer {
  // JUL logger for lifecycle messages
  private static final Logger LOGGER = Logger.getLogger(SimpleWebServer.class.getName());
  // Readiness indicator: becomes true only after the server has fully started
  private static final AtomicBoolean READY = new AtomicBoolean(false);

  /**
   * Application entry point: loads configuration, resolves port, and starts the
   * server.
   */
  public static void main(String[] args) {
    // Build root config (searches classpath resources + env + system properties)
    Config config = Config.create();
    // Use configured port or default to 8080 if absent
    int port = config.get("server.port").asInt().orElse(8080);
    WebServer server = startServer(config, port);
    LOGGER.log(Level.INFO, "Server is up! http://localhost:{0}", server.port());
  }

  /**
   * Starts the Helidon WebServer synchronously.
   * 
   * @param config application configuration root
   * @param port   desired port (0 for ephemeral random port)
   * @return started WebServer instance
   */
  public static WebServer startServer(Config config, int port) {
    Routing routing = createRouting(config);
    WebServer webServer = WebServer.builder()
        .routing(routing) // attach routes
        .port(port) // bind specified (or ephemeral) port
        .build()
        .start() // asynchronous start
        .await(); // wait until fully started
    READY.set(true); // flip readiness flag to UP
    return webServer;
  }

  /**
   * Builds routing tree including health endpoints and application handlers.
   * 
   * @param config configuration root
   * @return Routing instance
   */
  private static Routing createRouting(Config config) {
    // Resolve greeting (default fallback if not configured)
    String greeting = config.get("app.greeting").asString().orElse("Hello");

    // Custom readiness check uses the atomic flag to report state
    HealthCheck readiness = () -> READY.get()
        ? HealthCheckResponse.named("readiness").up().build()
        : HealthCheckResponse.named("readiness").down().build();

    // JVM thread management bean for deadlock detection
    ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();

    // Aggregate health support: adds liveness & readiness checks, exposes endpoints
    HealthSupport health = HealthSupport.builder()
        .addLiveness(HeapMemoryHealthCheck.create()) // heap usage liveness probe
        .addLiveness(DeadlockHealthCheck.create(threadMxBean)) // thread deadlock probe
        .addReadiness(readiness) // custom readiness probe
        .build(); // exposes /health (/live,/ready)

    // Compose routing
    return Routing.builder()
        .register(health) // /health, /health/live, /health/ready
        .get("/", (req, res) -> res.send(greeting + " @ " + Instant.now())) // dynamic greeting
        .get("/config", (req, res) -> res.send(config.toString())) // demo: config dump
        .build();
  }
}
