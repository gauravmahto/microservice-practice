package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ReadinessHealthCheck implements HealthCheck {
  @Override
  public HealthCheckResponse call() {
    // Replace with real dependencies (DB, client ping, etc.) when ready
    return HealthCheckResponse.up("basic-readiness");
  }
}
