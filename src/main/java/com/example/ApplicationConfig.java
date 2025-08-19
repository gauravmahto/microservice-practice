package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ApplicationPath;

/**
 * JAX-RS application configuration.
 */
@ApplicationScoped
@ApplicationPath("/")
public class ApplicationConfig extends Application {
    @Override
    public java.util.Set<Class<?>> getClasses() {
        return java.util.Set.of(GreetingResource.class, RunCheckResource.class);
    }
}
