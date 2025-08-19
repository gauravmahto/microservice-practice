package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * JAX-RS application class.
 * Registers all the resource classes for the application.
 */
@ApplicationScoped
@ApplicationPath("/")
public class RestApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(
        GreetingResource.class,
        ConfigResource.class,
        RootResource.class,
        RunCheckResource.class);
  }
}
