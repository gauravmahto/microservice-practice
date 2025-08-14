package com.example;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.health.checks.DeadlockHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer {
  private static final Logger LOGGER = Logger.getLogger(SimpleWebServer.class.getName());
  private static final AtomicBoolean READY = new AtomicBoolean(false);

  public static void main(String[] args) {
    Config config = Config.create();
    int port = config.get("server.port").asInt().orElse(8080);
    WebServer server = startServer(config, port);
    LOGGER.log(Level.INFO, "Server is up! http://localhost:{0}", server.port());
  }

  public static WebServer startServer(Config config, int port) {
    Routing routing = createRouting(config);
    WebServer webServer = WebServer.builder()
        .routing(routing)
        .port(port)
        .build()
        .start()
        .await();
    READY.set(true);
    return webServer;
  }

  private static Routing createRouting(Config config) {
    String greeting = config.get("app.greeting").asString().orElse("Hello");

    HealthCheck readiness = () -> READY.get()
        ? HealthCheckResponse.named("readiness").up().build()
        : HealthCheckResponse.named("readiness").down().build();
    ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
    HealthSupport health = HealthSupport.builder()
        .addLiveness(HeapMemoryHealthCheck.create())
        .addLiveness(DeadlockHealthCheck.create(threadMxBean))
        .addReadiness(readiness)
        .build();

    return Routing.builder()
        .register(health) // exposes /health, /health/live, /health/ready
        .get("/", (req, res) -> res.send(greeting + " @ " + Instant.now()))
        .get("/config", (req, res) -> res.send(config.toString()))
        .build();
  }
}
