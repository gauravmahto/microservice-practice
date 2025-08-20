package com.example;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes the Python readiness check script against a live server to verify
 * success path and a negative failure case.
 */
public class RunCheckScriptTest {

  private static WebServer server;

  @BeforeAll
  static void start() {
    server = SimpleWebServer.startServer(Config.create(), 0);
  }

  @AfterAll
  static void stop() {
    if (server != null)
      server.shutdown().await();
  }

  @Test
  void pythonCheckSucceedsAgainstReadyEndpoint() throws Exception {
    String url = "http://localhost:" + server.port() + "/health/ready";
    Process p = runCheckProcess(url, 10);
    int exit = waitFor(p, Duration.ofSeconds(15));
    String out = readAll(p);
    assertEquals(0, exit, () -> "Expected success exit code. Output:\n" + out);
    assertTrue(out.contains("SUCCESS"), () -> "Missing SUCCESS marker. Output:\n" + out);
  }

  @Test
  void pythonCheckFailsForUnreachableService() throws Exception {
    // Use an unlikely high port for failure; if bound, test still should fail due
    // to non-Helidon response.
    String url = "http://localhost:65530/health/ready"; // very high port typically unused
    Process p = runCheckProcess(url, 3);
    int exit = waitFor(p, Duration.ofSeconds(10));
    String out = readAll(p);
    assertNotEquals(0, exit, () -> "Expected non-zero exit for unreachable target. Output:\n" + out);
  }

  private static Process runCheckProcess(String url, int timeoutSeconds) throws IOException {
    // Prefer python3 if available, else fallback to python
    List<String> cmd = List.of("bash", "-c", "command -v python3 >/dev/null 2>&1 && echo python3 || echo python");
    Process which = new ProcessBuilder(cmd).start();
    try {
      which.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }
    String interpreter = readAll(which).trim();
    if (interpreter.isEmpty())
      interpreter = "python";
    ProcessBuilder pb = new ProcessBuilder(interpreter, "simple-check/check.py", "--url", url, "--timeout",
        String.valueOf(timeoutSeconds));
    pb.environment().put("TOTAL_TIMEOUT", String.valueOf(timeoutSeconds));
    pb.redirectErrorStream(true);
    return pb.start();
  }

  private static int waitFor(Process p, Duration timeout) throws InterruptedException {
    if (p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      return p.exitValue();
    }
    p.destroyForcibly();
    fail("Process timed out after " + timeout);
    return -1; // unreachable
  }

  private static String readAll(Process p) throws IOException {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null)
        sb.append(line).append('\n');
      return sb.toString();
    }
  }
}
