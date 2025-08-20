package com.example;

// Helidon configuration API (loads application.yaml / system / env)
import io.helidon.config.Config;
// Core web server & routing types
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
// Health aggregation & built‑in health checks
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.health.checks.DeadlockHealthCheck;
// MicroProfile Health SPI for custom readiness check
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

// Import Kubernetes client classes
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.BindException;
import java.util.concurrent.CompletionException;

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
  // Lazily-created Kubernetes client (avoid failing class initialization in test
  // envs without K8s config)
  private static volatile KubernetesClient k8s; // not final to allow lazy init

  private static KubernetesClient k8sClient() {
    // Optional toggle to disable Kubernetes integration entirely (e.g. in tests):
    // -Dk8s.disabled=true
    if (Boolean.getBoolean("k8s.disabled")) {
      return null;
    }
    KubernetesClient ref = k8s;
    if (ref == null) {
      synchronized (SimpleWebServer.class) {
        ref = k8s;
        if (ref == null) {
          try {
            ref = new KubernetesClientBuilder().build();
            k8s = ref;
          } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "Kubernetes client initialization failed; /run-check disabled: " + e.getMessage());
            k8s = null; // leave null so later attempts can retry (or remain disabled)
          }
        }
      }
    }
    return k8s;
  }

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
    // Ensure Kubernetes client (if created) is closed on JVM shutdown to free
    // resources
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      KubernetesClient client = k8s;
      if (client != null) {
        try {
          client.close();
          LOGGER.log(Level.INFO, "Closed Kubernetes client");
        } catch (Exception ex) {
          LOGGER.log(Level.WARNING, "Failed to close Kubernetes client: " + ex.getMessage(), ex);
        }
      }
      try {
        server.shutdown().await();
      } catch (Exception ignored) {
      }
    }));
  }

  /**
   * Starts the Helidon WebServer synchronously.
   * 
   * @param config application configuration root
   * @param port   desired port (0 for ephemeral random port)
   * @return started WebServer instance
   */
  public static WebServer startServer(Config config, int port) {
    try {
      WebServer ws = buildAndStart(config, port);
      READY.set(true);
      return ws;
    } catch (CompletionException | IllegalStateException e) {
      if (port != 0 && causedByBindException(e)) {
        LOGGER.log(Level.WARNING, () -> "Port " + port + " in use. Retrying with ephemeral port (0)...");
        WebServer ws = buildAndStart(config, 0);
        READY.set(true);
        return ws;
      }
      throw e;
    }
  }

  private static WebServer buildAndStart(Config config, int port) {
    Routing routing = createRouting(config);
    // Use deprecated routing(Routing) for now (no replacement method available in
    // current Helidon version)
    return WebServer.builder()
        .routing(routing)
        .port(port)
        .build()
        .start()
        .await();
  }

  private static boolean causedByBindException(Throwable t) {
    Throwable cur = t;
    while (cur != null) {
      if (cur instanceof BindException)
        return true;
      cur = cur.getCause();
    }
    return false;
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
        .post("/run-check", SimpleWebServer::handleRunCheck)
        // Human-readable flattened config dump (key=value per line)
        .get("/config", (req, res) -> {
          Map<String, String> flat = config.asMap().get(); // dotted keys
          String body = flat.entrySet().stream()
              // .filter(e -> e.getKey().startsWith("server.") ||
              // e.getKey().startsWith("app."))
              .sorted(Map.Entry.comparingByKey())
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining("\n"));
          if (body.isEmpty()) {
            body = "(no application keys found)";
          }
          res.headers().add("Content-Type", "text/plain; charset=UTF-8");
          res.send(body);
        })
        .build();
  }

  // Handler that creates a Kubernetes Job (if client available)
  private static void handleRunCheck(ServerRequest request, ServerResponse response) {
    try {
      KubernetesClient client = k8sClient();
      if (client == null) {
        response.status(503).send("Kubernetes client not available; feature disabled or init failed\n");
        return;
      }
      // Generate a unique name for the Job using a timestamp
      String jobName = "simple-check-job-" + System.currentTimeMillis();

      // Allow overriding image via query param or system property for flexibility
      String overrideImage = request.queryParams().first("image").orElse(null);
      if (overrideImage == null || overrideImage.isBlank()) {
        overrideImage = System.getProperty("check.image", "simple-check-app:latest");
      }

      // Target URL (optional) override; falls back to env-based defaults inside
      // script
      String targetUrl = request.queryParams().first("url").orElse(System.getProperty("check.target.url", ""));

      Job job = new JobBuilder()
          .withApiVersion("batch/v1")
          .withNewMetadata()
          .withName(jobName)
          .addToLabels("app", "simple-check")
          .endMetadata()
          .withNewSpec()
          .withNewTemplate()
          .withNewSpec()
          .addNewContainer()
          .withName("check-container")
          .withImage(overrideImage)
          .withImagePullPolicy("IfNotPresent")
          .addNewEnv().withName("TOTAL_TIMEOUT").withValue("30").endEnv()
          // Optional explicit target if provided
          .addAllToEnv(targetUrl == null || targetUrl.isBlank() ? java.util.List.of()
              : java.util.List.of(
                  new io.fabric8.kubernetes.api.model.EnvVarBuilder().withName("TARGET_URL").withValue(targetUrl)
                      .build()))
          .endContainer()
          .withRestartPolicy("Never")
          .endSpec()
          .endTemplate()
          .withBackoffLimit(0)
          .endSpec()
          .build();

      // Use the client to create the Job in the same namespace (fallback to 'default'
      // if null)
      String ns = client.getNamespace();
      if (ns == null || ns.isBlank()) {
        ns = "default"; // explicit fallback
      }
      client.batch().v1().jobs().inNamespace(ns).create(job);

      LOGGER.log(Level.INFO, "Successfully created Job: {0} in namespace {1}", new Object[] { jobName, ns });
      response.headers().add("Content-Type", "text/plain; charset=UTF-8");
      response.send("Successfully created Job: " + jobName + "\n");

    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to create Job: " + e.getMessage(), e);
      response.status(500).headers().add("Content-Type", "text/plain; charset=UTF-8");
      response.status(500).send("Failed to create Job: " + e.getMessage() + "\n");
    }
  }
}
