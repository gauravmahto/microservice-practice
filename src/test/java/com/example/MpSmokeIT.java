package com.example;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest
@AddBean(GreetingResource.class)
@AddBean(ConfigResource.class)
@AddBean(RootResource.class)
@AddBean(LivenessHealthCheck.class)
@AddBean(ReadinessHealthCheck.class)
@AddConfig(key = "server.port", value = "0") // let Helidon choose a free port
@AddConfig(key = "server.host", value = "127.0.0.1")
@AddConfig(key = "app.greeting", value = "Hello from test!")
class MpSmokeIT {

  @Inject
  WebTarget target; // already points at the running server

  @Test
  void openapiListsGreet() {
    Response r = target.path("/openapi").request().get(); // yaml by default
    String spec = r.readEntity(String.class);
    // helpful when diagnosing:
    // System.out.println(spec);
    assertTrue(spec.contains("/greet") || spec.contains("/api/greet"),
        "OpenAPI should list greet path; got:\n" + spec);
  }

  @Test
  void root() {
    Response r = target.path("/greet").request().get();
    String body = r.readEntity(String.class);
    assertEquals(200, r.getStatus(), "Expected 200, got " + r.getStatus() + " body=" + body);
    assertTrue(body.contains("Hello"), "Body should contain 'Hello', got: " + body);
  }

  @Test
  void health() {
    Response r = target.path("/health").request().get();
    assertEquals(200, r.getStatus(), "/health should be HTTP 200");
    assertTrue(r.readEntity(String.class).contains("\"status\":\"UP\""));
  }
}
