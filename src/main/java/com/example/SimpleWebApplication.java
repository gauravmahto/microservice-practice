package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

/**
 * Simple Helidon MP application demonstrating:
 * <ul>
 * <li>Config-driven port & greeting (microprofile-config.properties: server.port,
 * app.greeting)</li>
 * <li>Health endpoints: /health, /health/live, /health/ready via
 * MP Health</li>
 * <li>Custom readiness flag toggled after successful server start</li>
 * <li>Built-in liveness checks (heap memory & deadlock detection)</li>
 * <li>Sample endpoints: '/' returns greeting + timestamp, '/config' dumps
 * config (demo only)</li>
 * </ul>
 */
@ApplicationPath("/")
@ApplicationScoped
public class SimpleWebApplication extends Application {
    
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
            GreetingResource.class,
            ConfigResource.class,
            KubernetesJobResource.class
        );
    }
}