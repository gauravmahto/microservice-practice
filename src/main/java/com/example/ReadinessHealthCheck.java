package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom readiness health check that tracks application startup state.
 */
@Readiness
@ApplicationScoped
public class ReadinessHealthCheck implements HealthCheck {

    // Readiness indicator: becomes true only after the server has fully started
    private static final AtomicBoolean READY = new AtomicBoolean(true); // MP starts ready

    @Override
    public HealthCheckResponse call() {
        return READY.get()
                ? HealthCheckResponse.named("readiness").up().build()
                : HealthCheckResponse.named("readiness").down().build();
    }

    /**
     * Set readiness state.
     * 
     * @param ready true if application is ready, false otherwise
     */
    public static void setReady(boolean ready) {
        READY.set(ready);
    }

    /**
     * Check if application is ready.
     * 
     * @return true if application is ready
     */
    public static boolean isReady() {
        return READY.get();
    }
}