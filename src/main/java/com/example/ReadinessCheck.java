package com.example;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Readiness check toggled once application is initialized.
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        ready.set(true);
    }

    @Override
    public HealthCheckResponse call() {
        return ready.get() ? HealthCheckResponse.up("readiness")
                : HealthCheckResponse.down("readiness");
    }
}
