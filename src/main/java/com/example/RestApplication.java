package com.example;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

@ApplicationPath("/") // change to "/api" if you prefer a base path
public class RestApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(
        GreetingResource.class,
        ConfigResource.class,
        RunCheckResource.class // include others as you add them
    );
  }
}
